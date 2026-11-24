package com.blkdem.autoban.screening

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Этап 0: простой текстовый лог звонков. Будет заменён журналом в Room. */
object CallLogFile {
    private fun file(context: Context) = File(context.filesDir, "calls.log")

    @Synchronized
    fun append(context: Context, info: CallInfo, decision: String) {
        val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date(info.timestamp))
        val line = buildString {
            append(time).append(" | SIM").append(info.simSlot ?: "?")
            append(" (sub=").append(info.subscriptionId).append(", ").append(info.simLabel).append(")")
            append(" | номер=").append(info.rawNumber ?: "—").append(" [").append(info.presentation).append("]")
            append(" | имя=").append(info.callerName ?: "—").append(" [").append(info.callerNamePresentation).append("]")
            append(" | handle=").append(info.accountHandleId)
            append(" | ").append(decision)
        }
        file(context).appendText(line + "\n")
    }

    fun readLast(context: Context, n: Int = 100): List<String> =
        file(context).takeIf { it.exists() }?.readLines()?.takeLast(n)?.reversed().orEmpty()
}
