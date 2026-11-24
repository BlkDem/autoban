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

    fun sendMessage(chatId: String, html: String, silent: Boolean = false, buttons: List<Button> = emptyList()) {
        call("sendMessage", messageBody(chatId, html, silent, buttons))
    }

    /** Новые сообщения и нажатия кнопок начиная с offset. */
    fun updates(offset: Long): List<Update> {
        val result = call("getUpdates", buildJsonObject {
            put("offset", offset)
            put("limit", 100)
            put("timeout", 0)
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
            if (buttons.isNotEmpty()) {
                put("reply_markup", buildJsonObject {
                    put("inline_keyboard", JsonArray(listOf(JsonArray(buttons.map { b ->
                        buildJsonObject { put("text", b.text); put("callback_data", b.data) }
                    }))))
                })
            }
        }
    }

    private fun call(method: String, body: JsonObject): Any? {
        val conn = URL("$baseUrl/bot$token/$method").openConnection(proxy.toProxy()) as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
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
