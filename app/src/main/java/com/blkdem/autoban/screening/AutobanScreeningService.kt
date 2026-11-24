package com.blkdem.autoban.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log

class AutobanScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // Этап 0: ничего не блокируем, только пишем всё, что видим.
        respondToCall(details, CallResponse.Builder().build())
        try {
            val info = extract(details)
            Log.i(TAG, info.toString())
            CallLogFile.append(this, info, "ALLOW (логгер)")
        } catch (t: Throwable) {
            Log.e(TAG, "screening failed", t)
        }
    }

    private fun extract(d: Call.Details): CallInfo {
        val sim = SimResolver(this).resolve(d.accountHandle)
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

    private fun presentation(code: Int, value: String?) = when (code) {
        TelecomManager.PRESENTATION_ALLOWED ->
            if (value.isNullOrBlank()) CallInfo.Presentation.UNKNOWN else CallInfo.Presentation.PRESENTED
        TelecomManager.PRESENTATION_RESTRICTED -> CallInfo.Presentation.HIDDEN
        else -> CallInfo.Presentation.UNKNOWN
    }

    companion object { private const val TAG = "Autoban" }
}
