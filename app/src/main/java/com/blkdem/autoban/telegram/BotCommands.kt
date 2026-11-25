package com.blkdem.autoban.telegram

import com.blkdem.autoban.rules.Mask
import com.blkdem.autoban.rules.PhoneNumbers

/** Команды бота и кнопки под сообщениями. Чистый Kotlin — покрыт тестами. */
sealed interface BotCommand {
    data object Help : BotCommand
    data object Stats : BotCommand
    data class Last(val count: Int) : BotCommand
    data class Ban(val pattern: String) : BotCommand
    data class Unban(val pattern: String) : BotCommand
    data class White(val pattern: String) : BotCommand
    data class Unknown(val text: String) : BotCommand

    companion object {
        /** `/ban 8 912 345-67-89`, `/last 5`, `/stats@maxautobanbot`. */
        fun parse(text: String): BotCommand? {
            val t = text.trim()
            if (!t.startsWith("/")) return null
            val name = t.substringBefore(' ').substringBefore('@').lowercase()
            val arg = t.substringAfter(' ', "").trim()
            return when (name) {
                "/start", "/help" -> Help
                "/stats" -> Stats
                "/last" -> Last(arg.toIntOrNull()?.coerceIn(1, 50) ?: 10)
                "/ban" -> pattern(arg)?.let(::Ban) ?: Unknown(t)
                "/unban" -> pattern(arg)?.let(::Unban) ?: Unknown(t)
                "/white" -> pattern(arg)?.let(::White) ?: Unknown(t)
                else -> Unknown(t)
            }
        }

        /** Номер или маска в едином виде списков (см. [com.blkdem.autoban.rules.ListImport.canonical]). */
        fun pattern(arg: String): String? = com.blkdem.autoban.rules.ListImport.canonical(arg)

        /** callback_data кнопок: `ban:+79…`, `unban:+79…`, `white:+79…` (≤ 64 байт). */
        fun fromCallback(data: String): BotCommand? {
            val (op, num) = data.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val n = PhoneNumbers.normalize(num) ?: return null
            return when (op) {
                "ban" -> Ban(n)
                "unban" -> Unban(n)
                "white" -> White(n)
                else -> null
            }
        }

        const val HELP = "Команды:\n" +
            "/stats — сводка за сегодня и 7 дней\n" +
            "/last 10 — последние звонки\n" +
            "/ban &lt;номер или маска&gt; — в бан-лист\n" +
            "/unban &lt;номер&gt; — убрать из бан-листа\n" +
            "/white &lt;номер&gt; — в белый список\n\n" +
            "Кнопки под сообщениями о звонках делают то же самое."
    }
}
