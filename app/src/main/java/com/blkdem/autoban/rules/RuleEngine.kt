package com.blkdem.autoban.rules

import java.time.Instant
import java.time.ZoneId

/** Всё, что нужно движку помимо самого звонка. */
data class EvalContext(
    val rules: List<Rule>,
    val whitelist: List<ListEntry> = emptyList(),
    val blocklist: List<ListEntry> = emptyList(),
    /** Сколько звонков с этого номера было начиная с момента `sinceMillis` (без текущего). */
    val recentCalls: (number: String, sinceMillis: Long) -> Int = { _, _ -> 0 },
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Общий выключатель защиты. Выключено — всё пропускаем, но журналируем. */
    val protectionEnabled: Boolean = true,
)

data class Decision(
    val action: Action,
    val source: Source,
    val ruleId: Long? = null,
    val ruleName: String? = null,
    /** Правила в режиме DRY_RUN, которые сработали бы. */
    val dryRunHits: List<Rule> = emptyList(),
) {
    /** CONTACT — номер из контактов: фильтр звонков Android для них не вызывается, звонок увидели по «телефон звонит». */
    enum class Source { DEFAULT, WHITELIST, BLOCKLIST, RULE, DISABLED, MANUAL, CONTACT }

    val rejects: Boolean get() = action != Action.ALLOW

    /** Наиболее «важное» действие с учётом dry-run — что произошло бы, будь все правила активны. */
    val wouldAction: Action get() = if (source == Source.RULE || source == Source.BLOCKLIST) action
        else dryRunHits.firstOrNull()?.action ?: action
}

class RuleEngine {

    fun evaluate(call: CallInfo, ctx: EvalContext): Decision {
        val number = call.number

        if (number != null) {
            ctx.whitelist.firstOrNull { it.matches(number) }?.let {
                return Decision(Action.ALLOW, Decision.Source.WHITELIST, ruleName = "Белый список: ${it.pattern}")
            }
        }

        val dryRun = mutableListOf<Rule>()
        var hit: Decision? = null

        if (number != null) {
            ctx.blocklist.firstOrNull { it.matches(number) }?.let {
                hit = Decision(Action.REJECT, Decision.Source.BLOCKLIST, ruleName = "Бан-лист: ${it.pattern}")
            }
        }

        if (hit == null) {
            for (rule in ctx.rules.filter { it.enabled }.sortedWith(compareBy({ it.priority }, { it.id }))) {
                if (!matches(rule, call, ctx)) continue
                if (rule.mode == RuleMode.DRY_RUN) {
                    dryRun += rule
                    continue
                }
                val action = if (rule.action == Action.BAN && number == null) Action.REJECT else rule.action
                hit = Decision(action, Decision.Source.RULE, rule.id, rule.name)
                break
            }
        }

        val result = (hit ?: Decision(Action.ALLOW, Decision.Source.DEFAULT)).copy(dryRunHits = dryRun)
        return if (!ctx.protectionEnabled && result.rejects) {
            Decision(Action.ALLOW, Decision.Source.DISABLED, result.ruleId, result.ruleName, dryRun)
        } else {
            result
        }
    }

    fun matches(rule: Rule, call: CallInfo, ctx: EvalContext): Boolean =
        rule.conditions.all { matches(it, call, ctx) }

    fun matches(c: Condition, call: CallInfo, ctx: EvalContext): Boolean = when (c) {
        is Condition.SimSlot -> call.simSlot == c.slot
        Condition.NumberHidden -> call.isNumberHidden
        Condition.NumberPresented -> !call.isNumberHidden
        is Condition.CallerName -> !call.callerName.isNullOrBlank() && Mask(c.mask).matches(call.callerName)
        is Condition.NumberMask -> Mask.numberMatches(Mask.forNumber(c.mask), call.number)
        is Condition.TimeWindow -> inWindow(c, call.timestamp, ctx.zone)
        is Condition.Repeated -> call.number?.let { n ->
            ctx.recentCalls(n, call.timestamp - c.minutes * 60_000L) + 1 >= c.count
        } ?: false
        is Condition.Reputation -> reputation(c, call.report)
        is Condition.Marking -> CallLabel.parse(call.callerName)?.let { m ->
            (c.mask.isBlank() || Mask(c.mask).matches(call.callerName)) &&
                (c.organization.isBlank() || Mask(c.organization).matches(m.organization)) &&
                (c.category.isBlank() || Mask(c.category).matches(m.category))
        } ?: false
        Condition.Unmarked -> CallLabel.isUnmarked(call.callerName)
    }

    private fun reputation(c: Condition.Reputation, r: com.blkdem.autoban.lookup.NumberReport?): Boolean {
        if (r == null || !r.found || r.reviews == 0) return false
        if (r.reviews < c.minReviews) return false
        if (c.maxRating > 0 && (r.rating == null || r.rating > c.maxRating)) return false
        if (c.category.isNotBlank()) {
            val m = Mask(c.category)
            if (r.categories.none { m.matches(it) }) return false
        }
        return true
    }

    /** Нужны ли правилам сведения о номере: есть ли включённые правила с условием репутации. */
    fun needsReport(rules: List<Rule>, activeOnly: Boolean): Boolean = rules.any { r ->
        r.enabled && (!activeOnly || r.mode == RuleMode.ACTIVE) && r.conditions.any { it is Condition.Reputation }
    }

    private fun inWindow(c: Condition.TimeWindow, ts: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(ts).atZone(zone)
        val minute = t.hour * 60 + t.minute
        val day = t.dayOfWeek.value
        return if (c.fromMinute <= c.toMinute) {
            (c.days.isEmpty() || day in c.days) && minute >= c.fromMinute && minute < c.toMinute
        } else {
            // Через полночь: вечерняя часть относится к текущему дню, утренняя — к предыдущему.
            val prevDay = if (day == 1) 7 else day - 1
            (minute >= c.fromMinute && (c.days.isEmpty() || day in c.days)) ||
                (minute < c.toMinute && (c.days.isEmpty() || prevDay in c.days))
        }
    }
}
