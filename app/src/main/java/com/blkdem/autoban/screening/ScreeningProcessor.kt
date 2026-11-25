package com.blkdem.autoban.screening

import android.content.Context
import android.util.Log
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.data.Settings
import com.blkdem.autoban.lookup.NumberLookup
import com.blkdem.autoban.lookup.NumberReport
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
    private val lookup: NumberLookup,
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

    private fun lookupFor(info: CallInfo): String? =
        info.number?.takeIf { settings.lookupEnabled && lookup.supports(it) }

    /**
     * Сведения о номере до решения. Из кэша — всегда (это мгновенно). С сайта — только если их ждут
     * боевые правила, и не дольше [budgetMs]: звонок задерживается на время поиска.
     */
    fun reportBeforeDecision(info: CallInfo, budgetMs: Long): NumberReport? {
        val n = lookupFor(info) ?: return null
        lookup.cached(n)?.let { return it }
        if (!engine.needsReport(repo.rules(), activeOnly = true)) return null
        return lookup.lookup(n, budgetMs.coerceAtMost(MAX_WAIT_MS))
    }

    /** Тестер правил: решение без последствий. Сведения о номере ищутся, если они нужны хоть одному правилу. */
    fun simulate(info: CallInfo): Decision {
        val n = lookupFor(info)
        val report = if (n != null && engine.needsReport(repo.rules(), activeOnly = false)) lookup.lookup(n, 10_000) else null
        return decide(info.copy(report = report))
    }

    /** Сведения о номере для карточки и бота: из кэша или с сайта. */
    fun report(number: String, force: Boolean = false): NumberReport? = lookup.lookup(number, 15_000, force)

    fun record(info: CallInfo, decision: Decision, details: String?): CallEvent {
        val event = CallEvent.of(info, decision, details)
        val id = repo.insertEvent(event)
        val number = info.number
        if (decision.action == Action.BAN && decision.source == Decision.Source.RULE && number != null) {
            repo.addToList(ListType.BLOCK, number, note = info.callerName, ruleName = decision.ruleName)
        }
        val saved = event.copy(id = id)
        val n = lookupFor(info)
        if (info.report == null && n != null) {
            // Сначала узнаём о номере, потом сообщаем: в Telegram уйдёт уже со сводкой.
            lookup.lookupAsync(n) { report ->
                val updated = report?.let { attachReport(saved, info, decision, it) } ?: saved
                notifyListeners(updated)
            }
        } else {
            notifyListeners(saved)
        }
        cleanupIfNeeded()
        return saved
    }

    /** Записать сведения о номере в событие и пересчитать dry-run с их учётом. Само решение не меняется. */
    private fun attachReport(e: CallEvent, info: CallInfo, decision: Decision, report: NumberReport): CallEvent {
        val again = engine.evaluate(info.copy(report = report), context())
        val hits = again.dryRunHits.map { it.name }.toMutableList()
        val late = again.source == Decision.Source.RULE && decision.source != Decision.Source.RULE &&
            decision.source != Decision.Source.BLOCKLIST && decision.source != Decision.Source.WHITELIST
        if (late) hits.add(0, "${again.ruleName} (отзывы пришли позже)")
        val dryRun = hits.distinct().takeIf { it.isNotEmpty() }?.joinToString("; ")
        val would = when {
            decision.source == Decision.Source.RULE || decision.source == Decision.Source.BLOCKLIST -> decision.action
            late -> again.action
            else -> again.dryRunHits.firstOrNull()?.action ?: decision.action
        }
        repo.updateEventReport(e.id, report, dryRun, would)
        return repo.event(e.id) ?: e.copy(report = report, dryRun = dryRun, wouldAction = would)
    }

    private fun notifyListeners(e: CallEvent) {
        val ls = synchronized(listeners) { listeners.toList() }
        ls.forEach { runCatching { it(e) }.onFailure { t -> Log.e(TAG, "listener failed", t) } }
    }

    private fun cleanupIfNeeded() {
        val days = settings.retentionDays
        val now = System.currentTimeMillis()
        if (days <= 0 || now - settings.lastCleanup < 12 * 3600_000L) return
        settings.lastCleanup = now
        repo.deleteEventsBefore(now - days * 86_400_000L)
        lookup.cleanup(now)
    }

    companion object {
        private const val TAG = "Autoban"
        /** Дольше ждать отзывов до решения нельзя: на ответ системе ~5 секунд. */
        private const val MAX_WAIT_MS = 3000L
    }
}
