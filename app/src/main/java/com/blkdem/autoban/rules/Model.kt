package com.blkdem.autoban.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Action {
    /** Пропустить звонок. */
    ALLOW,
    /** Сбросить. */
    REJECT,
    /** Сбросить и добавить номер в бан-лист. */
    BAN,
}

@Serializable
enum class RuleMode {
    ACTIVE,
    /** Только записать в журнал «сработало бы», звонок не трогать. */
    DRY_RUN,
}

@Serializable
data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** Меньше — раньше проверяется. */
    val priority: Int = 100,
    val mode: RuleMode = RuleMode.DRY_RUN,
    /** Все условия должны выполниться (И). Пустой список — правило срабатывает на любой звонок. */
    val conditions: List<Condition> = emptyList(),
    val action: Action = Action.REJECT,
)

@Serializable
sealed interface Condition {

    /** Звонок пришёл на SIM в указанном слоте (1, 2…). */
    @Serializable @SerialName("sim")
    data class SimSlot(val slot: Int) : Condition

    /** Номер скрыт или не определён. */
    @Serializable @SerialName("hidden")
    data object NumberHidden : Condition

    /** Номер передан. */
    @Serializable @SerialName("presented")
    data object NumberPresented : Condition

    /** Имя, которое показывает сеть (CNAP / маркировка), совпадает с маской. `*` — «любое имя есть». */
    @Serializable @SerialName("name")
    data class CallerName(val mask: String) : Condition

    /** Номер совпадает с маской: `+7495*`, `8800*`, `+7912???????`. */
    @Serializable @SerialName("number")
    data class NumberMask(val mask: String) : Condition

    /**
     * Время звонка: дни недели (1 = пн … 7 = вс; пусто — любые) и интервал в минутах от полуночи.
     * Если from > to — интервал через полночь (например, 22:00–07:00).
     */
    @Serializable @SerialName("time")
    data class TimeWindow(
        val days: Set<Int> = emptySet(),
        val fromMinute: Int = 0,
        val toMinute: Int = 24 * 60,
    ) : Condition

    /** С этого номера это уже `count`-й звонок за последние `minutes` минут (включая текущий). */
    @Serializable @SerialName("repeat")
    data class Repeated(val count: Int = 2, val minutes: Int = 3) : Condition

    /**
     * Репутация номера по открытым отзывам (zvonili.com). Должно выполниться всё заданное:
     * отзывов не меньше `minReviews`, оценка не выше `maxRating` (0 — не учитывать),
     * хотя бы один тип номера совпадает с маской `category` (пусто — любой).
     * Нет сведений о номере (скрыт, не найден, сайт не ответил) — условие не выполняется.
     */
    @Serializable @SerialName("reputation")
    data class Reputation(
        val minReviews: Int = 3,
        val maxRating: Double = 2.0,
        val category: String = "",
    ) : Condition
}

enum class ListType { BLOCK, WHITE }

data class ListEntry(
    val id: Long = 0,
    val type: ListType,
    /** Точный номер (+7…) или маска. */
    val pattern: String,
    val note: String? = null,
    val createdAt: Long = 0,
    val ruleName: String? = null,
) {
    fun matches(normalizedNumber: String?): Boolean =
        Mask.numberMatches(Mask.forNumber(pattern), normalizedNumber)
}
