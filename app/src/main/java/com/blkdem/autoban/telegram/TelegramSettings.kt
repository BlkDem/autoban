package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Settings
import com.blkdem.autoban.rules.Action

/** Настройки Telegram. Токен хранится зашифрованным (см. [SecretStore]). */
class TelegramSettings(private val settings: Settings) {
    private val p get() = settings.raw

    enum class Mode { INSTANT, DIGEST }

    data class Snapshot(
        val enabled: Boolean,
        val token: String?,
        val chatId: String,
        val notifyRejected: Boolean,
        val notifyDryRun: Boolean,
        val notifyAllowed: Boolean,
        val silent: Boolean,
        val mode: Mode,
        val digestMinutes: Int,
        val proxy: TelegramClient.ProxyConfig,
        /** Кнопки под сообщениями и команды боту. */
        val commands: Boolean = true,
        /** Определитель номера: о каждом входящем сразу, пока телефон звонит, — и от контактов тоже. */
        val callerId: Boolean = false,
        /** Пересылать входящие SMS. */
        val forwardSms: Boolean = false,
    ) {
        val configured get() = !token.isNullOrBlank() && chatId.isNotBlank()

        /** Нужно ли сообщать об этом событии. */
        fun wants(e: CallEvent): Boolean = enabled && configured && when {
            e.action != Action.ALLOW -> notifyRejected
            callerId -> true
            e.dryRun != null -> notifyDryRun || notifyAllowed
            else -> notifyAllowed
        }

        fun client() = TelegramClient(token!!, proxy)
    }

    fun snapshot() = Snapshot(
        enabled = p.getBoolean("tg_enabled", false),
        token = p.getString("tg_token", null)?.let(SecretStore::decrypt),
        chatId = p.getString("tg_chat", "").orEmpty(),
        notifyRejected = p.getBoolean("tg_rejected", true),
        notifyDryRun = p.getBoolean("tg_dry", true),
        notifyAllowed = p.getBoolean("tg_allowed", false),
        silent = p.getBoolean("tg_silent", false),
        mode = runCatching { Mode.valueOf(p.getString("tg_mode", "INSTANT")!!) }.getOrDefault(Mode.INSTANT),
        digestMinutes = p.getInt("tg_digest_min", 30),
        proxy = TelegramClient.ProxyConfig(
            type = runCatching { TelegramClient.ProxyConfig.Type.valueOf(p.getString("tg_proxy_type", "NONE")!!) }
                .getOrDefault(TelegramClient.ProxyConfig.Type.NONE),
            host = p.getString("tg_proxy_host", "").orEmpty(),
            port = p.getInt("tg_proxy_port", 1080),
        ),
        commands = p.getBoolean("tg_commands", true),
        callerId = p.getBoolean("tg_callerid", false),
        forwardSms = com.blkdem.autoban.Features.smsForwarding && p.getBoolean("tg_sms", false),
    )

    /** offset для getUpdates — следующий необработанный update_id. */
    var offset: Long
        get() = p.getLong("tg_offset", 0)
        set(v) { p.edit().putLong("tg_offset", v).apply() }

    fun hasToken() = p.contains("tg_token")

    fun save(s: Snapshot, newToken: String?) {
        p.edit().apply {
            putBoolean("tg_enabled", s.enabled)
            if (newToken != null) {
                if (newToken.isBlank()) remove("tg_token") else putString("tg_token", SecretStore.encrypt(newToken.trim()))
            }
            putString("tg_chat", s.chatId.trim())
            putBoolean("tg_rejected", s.notifyRejected)
            putBoolean("tg_dry", s.notifyDryRun)
            putBoolean("tg_allowed", s.notifyAllowed)
            putBoolean("tg_silent", s.silent)
            putString("tg_mode", s.mode.name)
            putInt("tg_digest_min", s.digestMinutes.coerceIn(5, 24 * 60))
            putString("tg_proxy_type", s.proxy.type.name)
            putString("tg_proxy_host", s.proxy.host.trim())
            putInt("tg_proxy_port", s.proxy.port)
            putBoolean("tg_commands", s.commands)
            putBoolean("tg_callerid", s.callerId)
            putBoolean("tg_sms", s.forwardSms)
        }.apply()
        settings.notifyChanged()
    }
}
