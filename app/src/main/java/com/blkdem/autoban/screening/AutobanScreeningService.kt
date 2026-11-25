package com.blkdem.autoban.screening

import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import com.blkdem.autoban.AutobanApp
import com.blkdem.autoban.live.RadioLog
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import java.util.concurrent.Executors

class AutobanScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection == Call.Details.DIRECTION_OUTGOING
        ) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        // Решение принимаем вне главного потока: на ответ у нас ~5 секунд.
        val started = SystemClock.elapsedRealtime()
        executor.execute { screen(details, started) }
    }

    private fun screen(details: Call.Details, started: Long) {
        var responded = false
        try {
            val app = AutobanApp.from(this)
            val sim = SimResolver(this).resolve(details.accountHandle)
            var info = extract(details, sim)
            if (info.callerName == null) radioLabel(started)?.let {
                info = info.copy(callerName = it, callerNamePresentation = CallInfo.Presentation.PRESENTED)
            }
            val budget = RESPONSE_BUDGET_MS - (SystemClock.elapsedRealtime() - started)
            info = info.copy(report = app.processor.reportBeforeDecision(info, budget))
            val decision = app.processor.decide(info)
            respondToCall(details, response(decision))
            responded = true
            Log.i(TAG, "${info.number ?: "hidden"} sim=${info.simSlot}(${sim?.method}) name=${info.callerName} " +
                "report=${info.report?.summary()} -> ${decision.action} (${decision.ruleName}) " +
                "in ${SystemClock.elapsedRealtime() - started}ms")
            val raw = "sim=${sim?.slot} via ${sim?.method} (sub=${sim?.subscriptionId})\n" + describe(details)
            Log.d(TAG, raw)
            app.processor.record(info, decision, raw)
        } catch (t: Throwable) {
            Log.e(TAG, "screening failed", t)
            if (!responded) runCatching { respondToCall(details, CallResponse.Builder().build()) }
        }
    }

    /**
     * Android 10 не передаёт фильтру подпись оператора, но Samsung пишет её в системный лог раньше,
     * чем звонок приходит к нам. Если подписи пока нет — чуть подождём: у звонка без подписи ждём зря, но недолго.
     */
    private fun radioLabel(started: Long): String? {
        if (!RadioLog.available(this)) return null
        val since = System.currentTimeMillis() - RADIO_LOOKBACK_MS
        while (true) {
            RadioLog.label(this, since)?.let {
                Log.i(TAG, "radio label «$it» in ${SystemClock.elapsedRealtime() - started}ms")
                return it
            }
            if (SystemClock.elapsedRealtime() - started > RADIO_WAIT_MS) return null
            Thread.sleep(200)
        }
    }

    private fun response(d: Decision): CallResponse {
        val b = CallResponse.Builder()
        if (d.rejects) {
            b.setDisallowCall(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setRejectCall(true)
            // Пока только сброс: звонок остаётся в журнале вызовов и уведомлениях.
            b.setSkipCallLog(false).setSkipNotification(false)
        }
        return b.build()
    }

    private fun extract(d: Call.Details, sim: SimResolver.Sim?): CallInfo {
        val number = d.handle?.schemeSpecificPart
        return CallInfo(
            timestamp = System.currentTimeMillis(),
            rawNumber = number?.takeIf { it.isNotBlank() },
            presentation = presentation(d.handlePresentation, number),
            callerName = d.callerDisplayName?.takeIf { it.isNotBlank() },
            callerNamePresentation = presentation(d.callerDisplayNamePresentation, d.callerDisplayName),
            simSlot = sim?.slot,
            subscriptionId = sim?.subscriptionId,
            simLabel = sim?.label,
            accountHandleId = d.accountHandle?.id,
        )
    }

    private fun describe(d: Call.Details): String = buildString {
        append("handle=").append(d.handle)
        append("\nhandlePresentation=").append(d.handlePresentation)
        append("\ncallerDisplayName=").append(d.callerDisplayName)
        append("\ncallerDisplayNamePresentation=").append(d.callerDisplayNamePresentation)
        append("\naccountHandle=").append(d.accountHandle?.id).append(" / ").append(d.accountHandle?.componentName?.flattenToShortString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) append("\ncallerNumberVerificationStatus=").append(d.callerNumberVerificationStatus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) append("\ncallDirection=").append(d.callDirection)
        d.extras?.let { e -> for (k in e.keySet()) append("\nextra.").append(k).append('=').append(runCatching { e.get(k) }.getOrNull()) }
        d.intentExtras?.let { e -> for (k in e.keySet()) append("\nintentExtra.").append(k).append('=').append(runCatching { e.get(k) }.getOrNull()) }
    }

    private fun presentation(code: Int, value: String?) = when (code) {
        TelecomManager.PRESENTATION_ALLOWED ->
            if (value.isNullOrBlank()) CallInfo.Presentation.UNKNOWN else CallInfo.Presentation.PRESENTED
        TelecomManager.PRESENTATION_RESTRICTED -> CallInfo.Presentation.HIDDEN
        else -> CallInfo.Presentation.UNKNOWN
    }

    companion object {
        private const val TAG = "Autoban"
        /** Система ждёт ответа ~5 секунд; оставляем запас. */
        private const val RESPONSE_BUDGET_MS = 4000L
        /** Подпись пишется в лог при приходе звонка — за доли секунды до фильтра. */
        private const val RADIO_LOOKBACK_MS = 3000L
        private const val RADIO_WAIT_MS = 800L
        private val executor = Executors.newSingleThreadExecutor()
    }
}
