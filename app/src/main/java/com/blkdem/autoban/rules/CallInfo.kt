package com.blkdem.autoban.rules

/** Всё, что система сообщила о входящем звонке, в независимом от Android виде. */
data class CallInfo(
    val timestamp: Long,
    val rawNumber: String?,
    /** PRESENTED — передан, HIDDEN — скрыт, UNKNOWN — не определён. */
    val presentation: Presentation,
    val callerName: String? = null,
    val callerNamePresentation: Presentation = Presentation.UNKNOWN,
    val simSlot: Int? = null,          // 1, 2 …; null — не удалось определить
    val subscriptionId: Int? = null,
    val simLabel: String? = null,
    val accountHandleId: String? = null,
    /** Сведения о номере из отзывов, если успели их получить. */
    val report: com.blkdem.autoban.lookup.NumberReport? = null,
) {
    enum class Presentation { PRESENTED, HIDDEN, UNKNOWN }

    /** Номер в каноническом виде (+7…), null — если номера нет. */
    val number: String? by lazy {
        if (presentation == Presentation.PRESENTED) PhoneNumbers.normalize(rawNumber) else null
    }

    val isNumberHidden: Boolean get() = number == null
}
