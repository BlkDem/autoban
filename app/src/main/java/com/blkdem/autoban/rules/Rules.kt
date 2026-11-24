package com.blkdem.autoban.rules

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object Rules {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "type"
    }

    fun conditionsToJson(list: List<Condition>): String =
        json.encodeToString(ListSerializer(Condition.serializer()), list)

    fun conditionsFromJson(s: String): List<Condition> =
        json.decodeFromString(ListSerializer(Condition.serializer()), s)

    fun rulesToJson(list: List<Rule>): String =
        Json(json) { prettyPrint = true }.encodeToString(ListSerializer(Rule.serializer()), list)

    fun rulesFromJson(s: String): List<Rule> = json.decodeFromString(ListSerializer(Rule.serializer()), s)

    /** Предустановленные правила (по требованиям владельца). Создаются в режиме DRY_RUN. */
    val defaults = listOf(
        Rule(
            name = "Скрытые на SIM1",
            priority = 10,
            mode = RuleMode.DRY_RUN,
            conditions = listOf(Condition.SimSlot(1), Condition.NumberHidden),
            action = Action.REJECT,
        ),
        Rule(
            name = "Маркированные на SIM2",
            priority = 20,
            mode = RuleMode.DRY_RUN,
            conditions = listOf(Condition.SimSlot(2), Condition.NumberPresented, Condition.CallerName("*ростелеком*")),
            action = Action.BAN,
        ),
    )

    /** Человекочитаемое описание условия. */
    fun describe(c: Condition): String = when (c) {
        is Condition.SimSlot -> "SIM${c.slot}"
        Condition.NumberHidden -> "номер скрыт"
        Condition.NumberPresented -> "номер передан"
        is Condition.CallerName -> if (c.mask == "*") "есть имя от сети" else "имя ~ «${c.mask}»"
        is Condition.NumberMask -> "номер ~ ${c.mask}"
        is Condition.TimeWindow -> buildString {
            if (c.days.isNotEmpty()) append(c.days.sorted().joinToString(",") { DAYS[it - 1] }).append(' ')
            append(hm(c.fromMinute)).append('–').append(hm(c.toMinute))
        }
        is Condition.Repeated -> "${c.count}-й звонок за ${c.minutes} мин"
    }

    fun describe(a: Action): String = when (a) {
        Action.ALLOW -> "пропустить"
        Action.REJECT -> "сбросить"
        Action.BAN -> "сбросить и забанить"
    }

    val DAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    fun hm(m: Int) = "%02d:%02d".format(m / 60 % 24, m % 60).let { if (m >= 24 * 60) "24:00" else it }
}
