package com.blkdem.autoban.screening

import android.content.Context
import android.util.Log
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.data.Settings
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.EvalContext
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.RuleEngine

/** Принимает решение по звонку и записывает последствия (журнал, бан-лист, уведомления). */
class ScreeningProcessor(
    private val context: Context,
    private val repo: Repository,
    private val settings: Settings,
) {
    private val engine = RuleEngine()

    /** Список слушателей, которым передаётся записанное событие (Telegram и т.п.). */
    private val listeners = mutableListOf<(CallEvent) -> Unit>()
    fun addListener(l: (CallEvent) -> Unit) { synchronized(listeners) { listeners += l } }

    fun context(): EvalContext = EvalContext(
        rules = repo.rules(),
        whitelist = repo.list(ListType.WHITE),
        blocklist = repo.list(ListType.BLOCK),
        recentCalls = repo::countCallsFrom,
        protectionEnabled = settings.protectionEnabled,
    )

    fun decide(info: CallInfo): Decision = engine.evaluate(info, context())

    /** Тестер правил: решение без последствий. */
    fun simulate(info: CallInfo): Decision = decide(info)

    fun record(info: CallInfo, decision: Decision, details: String?): CallEvent {
        val event = CallEvent.of(info, decision, details)
        val id = repo.insertEvent(event)
        val number = info.number
        if (decision.action == Action.BAN && decision.source == Decision.Source.RULE && number != null) {
            repo.addToList(ListType.BLOCK, number, note = info.callerName, ruleName = decision.ruleName)
        }
        val saved = event.copy(id = id)
        val ls = synchronized(listeners) { listeners.toList() }
        ls.forEach { runCatching { it(saved) }.onFailure { e -> Log.e(TAG, "listener failed", e) } }
        cleanupIfNeeded()
        return saved
    }

    private fun cleanupIfNeeded() {
        val days = settings.retentionDays
        val now = System.currentTimeMillis()
        if (days <= 0 || now - settings.lastCleanup < 12 * 3600_000L) return
        settings.lastCleanup = now
        repo.deleteEventsBefore(now - days * 86_400_000L)
    }

    companion object { private const val TAG = "Autoban" }
}
