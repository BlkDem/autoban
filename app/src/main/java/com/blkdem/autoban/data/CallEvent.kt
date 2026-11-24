package com.blkdem.autoban.data

import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision

/** Запись журнала. */
data class CallEvent(
    val id: Long = 0,
    val timestamp: Long,
    val rawNumber: String?,
    val number: String?,
    val presentation: CallInfo.Presentation,
    val callerName: String?,
    val simSlot: Int?,
    val simLabel: String?,
    val action: Action,
    val source: Decision.Source,
    val ruleId: Long?,
    val ruleName: String?,
    /** Названия DRY_RUN-правил, которые сработали бы, через «; ». */
    val dryRun: String?,
    /** Действие, которое было бы применено, будь DRY_RUN-правила активны. */
    val wouldAction: Action,
    val tgStatus: TgStatus = TgStatus.NONE,
    /** Сырые поля Call.Details — для отладки. */
    val details: String? = null,
) {
    enum class TgStatus { NONE, PENDING, SENT, FAILED }

    companion object {
        fun of(info: CallInfo, d: Decision, details: String?) = CallEvent(
            timestamp = info.timestamp,
            rawNumber = info.rawNumber,
            number = info.number,
            presentation = info.presentation,
            callerName = info.callerName,
            simSlot = info.simSlot,
            simLabel = info.simLabel,
            action = d.action,
            source = d.source,
            ruleId = d.ruleId,
            ruleName = d.ruleName,
            dryRun = d.dryRunHits.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.name },
            wouldAction = d.wouldAction,
            details = details,
        )
    }
}
