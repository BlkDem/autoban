package com.blkdem.autoban.telegram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Минимальный клиент Bot API на HttpURLConnection. */
class TelegramClient(
    private val token: String,
    private val proxy: ProxyConfig = ProxyConfig(),
    private val baseUrl: String = "https://api.telegram.org",
) {
    data class ProxyConfig(val type: Type = Type.NONE, val host: String = "", val port: Int = 0) {
        enum class Type { NONE, HTTP, SOCKS }

        fun toProxy(): Proxy = when (type) {
            Type.NONE -> Proxy.NO_PROXY
            Type.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
            Type.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
        }
    }

    class TelegramException(message: String, val retryable: Boolean) : Exception(message)

    data class Chat(val id: Long, val title: String)

    data class Button(val text: String, val data: String)

    data class Update(val id: Long, val chatId: Long?, val text: String?, val callbackId: String?, val callbackData: String?)

    /** Отправить сообщение; возвращает message_id. */
    fun sendMessage(chatId: String, html: String, silent: Boolean = false, buttons: List<Button> = emptyList()): Long {
        val msg = call("sendMessage", messageBody(chatId, html, silent, buttons)) as? JsonObject
        return msg?.get("message_id")?.jsonPrimitive?.long ?: 0
    }

    /** Заменить текст и кнопки сообщения. «Не изменилось» ошибкой не считается. */
    fun editMessage(chatId: String, messageId: Long, html: String, buttons: List<Button> = emptyList()) {
        try {
            call("editMessageText", buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", html)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", true)
                put("reply_markup", keyboard(buttons))
            })
        } catch (e: TelegramException) {
            if (e.message?.contains("not modified") != true) throw e
        }
    }

    /**
     * Новые сообщения и нажатия кнопок начиная с offset. [timeoutSec] > 0 — длинный опрос:
     * Telegram держит запрос, пока не появится обновление (так кнопка срабатывает за секунду).
     */
    fun updates(offset: Long, timeoutSec: Int = 0): List<Update> {
        val result = call("getUpdates", buildJsonObject {
            put("offset", offset)
            put("limit", 100)
            put("timeout", timeoutSec)
            put("allowed_updates", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("message"), kotlinx.serialization.json.JsonPrimitive("callback_query"))))
        }) as? JsonArray ?: return emptyList()
        return result.map { el ->
            val u = el.jsonObject
            val id = u["update_id"]!!.jsonPrimitive.long
            val msg = u["message"]?.jsonObject
            val cb = u["callback_query"]?.jsonObject
            val chat = (msg ?: cb?.get("message")?.jsonObject)?.get("chat")?.jsonObject
            Update(
                id = id,
                chatId = chat?.get("id")?.jsonPrimitive?.long,
                text = msg?.get("text")?.jsonPrimitive?.contentOrNull,
                callbackId = cb?.get("id")?.jsonPrimitive?.contentOrNull,
                callbackData = cb?.get("data")?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    fun answerCallback(callbackId: String, text: String) {
        call("answerCallbackQuery", buildJsonObject { put("callback_query_id", callbackId); put("text", text) })
    }

    /** Чаты из последних входящих сообщений боту — чтобы найти chat_id. */
    fun recentChats(): List<Chat> {
        val result = call("getUpdates", buildJsonObject { put("limit", 50) }) as? JsonArray ?: return emptyList()
        return result.mapNotNull { upd ->
            val msg = upd.jsonObject["message"]?.jsonObject ?: upd.jsonObject["my_chat_member"]?.jsonObject ?: return@mapNotNull null
            val chat = msg["chat"]?.jsonObject ?: return@mapNotNull null
            val id = chat["id"]?.jsonPrimitive?.long ?: return@mapNotNull null
            val title = listOf("title", "username", "first_name").firstNotNullOfOrNull { chat[it]?.jsonPrimitive?.contentOrNull } ?: id.toString()
            Chat(id, title)
        }.distinctBy { it.id }.reversed()
    }

    companion object {
        fun messageBody(chatId: String, html: String, silent: Boolean, buttons: List<Button>): JsonObject = buildJsonObject {
            put("chat_id", chatId)
            put("text", html)
            put("parse_mode", "HTML")
            put("disable_web_page_preview", true)
            put("disable_notification", silent)
            if (buttons.isNotEmpty()) put("reply_markup", keyboard(buttons))
        }

        /** Кнопки в один ряд. Пустой список — убрать кнопки. */
        fun keyboard(buttons: List<Button>): JsonObject = buildJsonObject {
            put("inline_keyboard", JsonArray(if (buttons.isEmpty()) emptyList() else listOf(JsonArray(buttons.map { b ->
                buildJsonObject { put("text", b.text); put("callback_data", b.data) }
            }))))
        }
    }

    data class PinnedDocument(val fileId: String, val fileName: String?, val dateSeconds: Long, val caption: String?)

    /** Отправить файл; возвращает message_id. */
    fun sendDocument(chatId: String, fileName: String, bytes: ByteArray, caption: String, silent: Boolean = true): Long {
        val boundary = "autoban-${System.nanoTime()}"
        val body = java.io.ByteArrayOutputStream().apply {
            fun text(s: String) = write(s.toByteArray(Charsets.UTF_8))
            fun field(name: String, value: String) = text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            field("chat_id", chatId)
            field("caption", caption)
            field("disable_notification", silent.toString())
            text("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\nContent-Type: application/json\r\n\r\n")
            write(bytes)
            text("\r\n--$boundary--\r\n")
        }.toByteArray()
        val msg = request("sendDocument", "multipart/form-data; boundary=$boundary", body) as JsonObject
        return msg["message_id"]!!.jsonPrimitive.long
    }

    fun pinMessage(chatId: String, messageId: Long) {
        call("pinChatMessage", buildJsonObject { put("chat_id", chatId); put("message_id", messageId); put("disable_notification", true) })
    }

    /** Последнее закреплённое в чате сообщение, если это файл. */
    fun pinnedDocument(chatId: String): PinnedDocument? {
        val chat = call("getChat", buildJsonObject { put("chat_id", chatId) }) as? JsonObject ?: return null
        val msg = chat["pinned_message"] as? JsonObject ?: return null
        val doc = msg["document"] as? JsonObject ?: return null
        return PinnedDocument(
            fileId = doc["file_id"]!!.jsonPrimitive.content,
            fileName = doc["file_name"]?.jsonPrimitive?.contentOrNull,
            dateSeconds = msg["date"]?.jsonPrimitive?.long ?: 0,
            caption = msg["caption"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** Скачать файл по file_id (до 20 МБ — ограничение Bot API). */
    fun download(fileId: String): ByteArray {
        val file = call("getFile", buildJsonObject { put("file_id", fileId) }) as JsonObject
        val path = file["file_path"]?.jsonPrimitive?.contentOrNull ?: throw TelegramException("Файл недоступен", retryable = false)
        val conn = URL("$baseUrl/file/bot$token/$path").openConnection(proxy.toProxy()) as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) throw TelegramException("HTTP ${conn.responseCode}", retryable = conn.responseCode >= 500)
            return conn.inputStream.use { it.readBytes() }
        } catch (e: TelegramException) {
            throw e
        } catch (e: Exception) {
            throw TelegramException(e.message ?: e.javaClass.simpleName, retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    private fun call(method: String, body: JsonObject): Any? =
        request(method, "application/json; charset=utf-8", body.toString().toByteArray(Charsets.UTF_8))

    private fun request(method: String, contentType: String, body: ByteArray): Any? {
        val conn = URL("$baseUrl/bot$token/$method").openConnection(proxy.toProxy()) as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            val ok = json?.get("ok")?.jsonPrimitive?.boolean == true
            if (!ok) {
                val desc = json?.get("description")?.jsonPrimitive?.contentOrNull ?: "HTTP $code"
                // 4xx (кроме 429) — ошибка настройки: неверный токен/чат, повторять бессмысленно.
                throw TelegramException(desc, retryable = code == 429 || code >= 500 || json == null)
            }
            return json["result"]
        } catch (e: TelegramException) {
            throw e
        } catch (e: Exception) {
            throw TelegramException(e.message ?: e.javaClass.simpleName, retryable = true)
        } finally {
            conn.disconnect()
        }
    }
}
