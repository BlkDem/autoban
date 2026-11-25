package com.blkdem.autoban.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.Condition
import com.blkdem.autoban.rules.Mask
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.rules.RuleMode
import com.blkdem.autoban.rules.Rules

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditor(
    initial: Rule,
    onSave: (Rule) -> Unit,
    onDelete: (Rule) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var priorityText by remember(initial) { mutableStateOf(initial.priority.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (initial.id == 0L) "Новое правило" else "Правило", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Боевой режим", fontWeight = FontWeight.SemiBold)
                Text(
                    if (draft.mode == RuleMode.ACTIVE) "Правило применяется к звонкам"
                    else "Dry-run: только запись «сработало бы»",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(draft.mode == RuleMode.ACTIVE, { draft = draft.copy(mode = if (it) RuleMode.ACTIVE else RuleMode.DRY_RUN) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Включено", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(draft.enabled, { draft = draft.copy(enabled = it) })
        }
        OutlinedTextField(
            priorityText, { priorityText = it.filter(Char::isDigit).take(4) },
            label = { Text("Приоритет (меньше — раньше)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )

        Text("Действие", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action.entries.forEach { a ->
                FilterChip(draft.action == a, { draft = draft.copy(action = a) }, label = { Text(Rules.describe(a)) })
            }
        }

        Text("Условия (все одновременно)", fontWeight = FontWeight.SemiBold)
        if (draft.conditions.isEmpty()) {
            Text("Нет условий — правило сработает на любой звонок.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        draft.conditions.forEachIndexed { i, c ->
            ConditionCard(
                c,
                onChange = { nc -> draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[i] = nc }) },
                onRemove = { draft = draft.copy(conditions = draft.conditions.toMutableList().also { it.removeAt(i) }) },
            )
        }
        AddConditionButton { draft = draft.copy(conditions = draft.conditions + it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(draft.copy(name = draft.name.ifBlank { "Без названия" }, priority = priorityText.toIntOrNull() ?: 100)) },
            ) { Text("Сохранить") }
            OutlinedButton(onClick = onClose) { Text("Отмена") }
            if (initial.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить правило «${initial.name}»?") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete(initial) }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AddConditionButton(onAdd: (Condition) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("+ Добавить условие") }
        DropdownMenu(open, { open = false }) {
            listOf(
                "SIM" to Condition.SimSlot(1),
                "Номер скрыт" to Condition.NumberHidden,
                "Номер передан" to Condition.NumberPresented,
                "Имя от сети по маске" to Condition.CallerName("*"),
                "Номер по маске" to Condition.NumberMask("+7"),
                "Время и дни" to Condition.TimeWindow(fromMinute = 9 * 60, toMinute = 21 * 60),
                "Повторный звонок" to Condition.Repeated(2, 3),
                "Репутация по отзывам" to Condition.Reputation(),
            ).forEach { (label, c) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onAdd(c) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionCard(c: Condition, onChange: (Condition) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Rules.describe(c), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Убрать") }
            }
            when (c) {
                is Condition.SimSlot -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..2).forEach { s -> FilterChip(c.slot == s, { onChange(Condition.SimSlot(s)) }, label = { Text("SIM$s") }) }
                }
                Condition.NumberHidden, Condition.NumberPresented -> {}
                is Condition.CallerName -> MaskField(
                    c.mask, { onChange(Condition.CallerName(it)) },
                    hint = "Например: *ростелеком*  ·  * — любое имя  ·  re:… — регулярка",
                    test = { mask, sample -> Mask(mask).matches(sample) }, sampleLabel = "Проверить на имени",
                    valid = { Mask(it).isValid },
                )
                is Condition.NumberMask -> MaskField(
                    c.mask, { onChange(Condition.NumberMask(it)) },
                    hint = "Например: +7495*  ·  8800*  ·  +7912???????",
                    test = { mask, sample ->
                        Mask.numberMatches(Mask.forNumber(mask), com.blkdem.autoban.rules.PhoneNumbers.normalize(sample))
                    },
                    sampleLabel = "Проверить на номере",
                    valid = { Mask.forNumber(it).isValid },
                )
                is Condition.TimeWindow -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Rules.DAYS.forEachIndexed { i, d ->
                            val day = i + 1
                            FilterChip(day in c.days, {
                                onChange(c.copy(days = if (day in c.days) c.days - day else c.days + day))
                            }, label = { Text(d) })
                        }
                    }
                    Text("Ни один день не выбран — любой день", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField("С", c.fromMinute, Modifier.weight(1f)) { onChange(c.copy(fromMinute = it)) }
                        TimeField("До", c.toMinute, Modifier.weight(1f)) { onChange(c.copy(toMinute = it)) }
                    }
                }
                is Condition.Repeated -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Звонок №", c.count, Modifier.weight(1f)) { onChange(c.copy(count = it.coerceAtLeast(2))) }
                    NumberField("За минут", c.minutes, Modifier.weight(1f)) { onChange(c.copy(minutes = it.coerceAtLeast(1))) }
                }
                is Condition.Reputation -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Отзывов не меньше", c.minReviews, Modifier.weight(1f)) { onChange(c.copy(minReviews = it.coerceAtLeast(1))) }
                        RatingField(c.maxRating, Modifier.weight(1f)) { onChange(c.copy(maxRating = it)) }
                    }
                    OutlinedTextField(
                        c.category, { onChange(c.copy(category = it.trim())) }, label = { Text("Тип номера (маска)") }, singleLine = true,
                        isError = !Mask(c.category).isValid, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Например: *мошен*  ·  *коллект*  ·  пусто — любой тип") },
                    )
                    Text(
                        "Отзывы берутся с zvonili.com. В боевом режиме звонок задерживается до 3 секунд, пока идёт поиск " +
                            "(повторные звонки — из кэша, мгновенно). В dry-run звонок не задерживается.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MaskField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    test: (String, String) -> Boolean,
    sampleLabel: String,
    valid: (String) -> Boolean,
) {
    var sample by remember { mutableStateOf("") }
    val ok = valid(value)
    OutlinedTextField(
        value, onChange, label = { Text("Маска") }, singleLine = true, isError = !ok,
        supportingText = { Text(if (ok) hint else "Ошибка в регулярном выражении") }, modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(sample, { sample = it }, label = { Text(sampleLabel) }, singleLine = true, modifier = Modifier.weight(1f))
        if (sample.isNotEmpty()) {
            Text(
                if (test(value, sample)) "✅ подходит" else "— нет",
                modifier = Modifier.padding(start = 8.dp).width(96.dp),
            )
        }
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(minutes) { mutableStateOf(Rules.hm(minutes)) }
    val parsed = parseHm(text)
    OutlinedTextField(
        text, { text = it; parseHm(it)?.let(onChange) }, label = { Text(label) }, singleLine = true,
        isError = parsed == null, modifier = modifier,
    )
}

/** Оценка 0…5 с дробной частью; 0 — не учитывать. */
@Composable
private fun RatingField(value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(com.blkdem.autoban.lookup.NumberReport.fmtRating(value)) }
    fun parse(t: String) = t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..5.0 }
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(4); parse(text)?.let(onChange) },
        label = { Text("Оценка не выше") }, singleLine = true, isError = parse(text) == null,
        supportingText = { Text("0 — не важно") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter(Char::isDigit).take(3); text.toIntOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
    )
}

/** "9:30" / "09:30" / "24:00" → минуты от полуночи. */
fun parseHm(s: String): Int? {
    val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").find(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    if (min > 59 || h > 24 || (h == 24 && min != 0)) return null
    return h * 60 + min
}
