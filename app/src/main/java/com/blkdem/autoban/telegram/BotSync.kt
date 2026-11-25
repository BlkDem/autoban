package com.blkdem.autoban.telegram

import android.os.Build
import com.blkdem.autoban.data.Backup
import com.blkdem.autoban.data.Repository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Перенос правил и списков между телефонами через бота, без своего сервера.
 *
 * Телефон А отправляет в чат файл копии и закрепляет его. Телефон Б с тем же ботом и chat_id
 * читает закреплённое сообщение через getChat — getUpdates не годится: сообщения самого бота
 * туда не попадают, а offset у двух телефонов мешал бы друг другу.
 */
object BotSync {
    const val FILE_PREFIX = "autoban-backup"

    data class Fetched(val backup: Backup, val dateMillis: Long, val caption: String?)

    /** Отправить копию в чат и закрепить. Возвращает текст для пользователя. */
    fun upload(repo: Repository, s: TelegramSettings.Snapshot): String {
        val b = repo.exportBackup()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val caption = "Autoban: правила и списки\n${deviceName()} · бан ${b.blocklist.size}, белых ${b.whitelist.size}, правил ${b.rules.size}\n" +
            "На другом телефоне: Настройки → «Загрузить из бота»"
        val client = s.client()
        val id = client.sendDocument(s.chatId, "$FILE_PREFIX-$stamp.json", Backup.encode(b).toByteArray(Charsets.UTF_8), caption)
        client.pinMessage(s.chatId, id)
        return "Отправлено и закреплено: бан ${b.blocklist.size}, белых ${b.whitelist.size}, правил ${b.rules.size}"
    }

    /** Скачать закреплённую копию. */
    fun fetch(s: TelegramSettings.Snapshot): Fetched {
        val client = s.client()
        val doc = client.pinnedDocument(s.chatId)
            ?: throw TelegramClient.TelegramException("В чате нет закреплённой копии. Сначала нажмите «Отправить в бота» на другом телефоне.", retryable = false)
        if (doc.fileName?.startsWith(FILE_PREFIX) != true) {
            throw TelegramClient.TelegramException("Закреплён не файл Autoban (${doc.fileName ?: "без имени"})", retryable = false)
        }
        val backup = runCatching { Backup.decode(client.download(doc.fileId).toString(Charsets.UTF_8)) }
            .getOrElse { if (it is TelegramClient.TelegramException) throw it else throw TelegramClient.TelegramException("Файл повреждён: ${it.message}", retryable = false) }
        return Fetched(backup, doc.dateSeconds * 1000, doc.caption)
    }

    private fun deviceName(): String {
        val m = Build.MODEL.orEmpty()
        val brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        return if (m.startsWith(brand, ignoreCase = true)) m else "$brand $m".trim()
    }
}
