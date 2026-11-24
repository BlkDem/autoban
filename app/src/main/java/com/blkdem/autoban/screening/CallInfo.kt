package com.blkdem.autoban.screening

/** Всё, что система сообщила о входящем звонке, в независимом от Android виде. */
data class CallInfo(
    val timestamp: Long,
    val rawNumber: String?,
    /** PRESENTED — передан, HIDDEN — скрыт, UNKNOWN — не определён. */
    val presentation: Presentation,
    val callerName: String?,
    val callerNamePresentation: Presentation,
    val simSlot: Int?,          // 1, 2 …; null — не удалось определить
    val subscriptionId: Int?,
    val simLabel: String?,
    val accountHandleId: String?,
) {
    enum class Presentation { PRESENTED, HIDDEN, UNKNOWN }
}
