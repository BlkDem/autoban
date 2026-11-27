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
    /** Сведения о номере из отзывов (см. [com.blkdem.autoban.lookup.NumberLookup]). */
    val report: com.blkdem.autoban.lookup.NumberReport? = null,
    /** Чем кончился пропущенный фильтром звонок (null — звонок сброшен фильтром или не отслеживался). */
    val outcome: Outcome? = null,
    /** Сообщение в Telegram о звонке — чтобы обновлять его, пока звонок идёт. */
    val tgMessageId: Long? = null,
    /** Откуда взята подпись [callerName]. */
    val labelSource: CallInfo.LabelSource? = null,
    /** Имя из контактов телефона. */
    val contactName: String? = null,
) {
    enum class TgStatus { NONE, PENDING, SENT, FAILED }

    enum class Outcome {
        /** Телефон звонит прямо сейчас. */
        RINGING,
        ANSWERED,
        /** Не ответили (или отклонили в звонилке). */
        MISSED,
        /** Сброшен кнопкой в Telegram. */
        HUNG_UP_BOT,
        /** Сброшен по правилу, когда пришла подпись оператора. */
        HUNG_UP_RULE,
    }

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
            report = info.report,
            labelSource = info.callerNameSource.takeIf { info.callerName != null },
        )
    }
}
