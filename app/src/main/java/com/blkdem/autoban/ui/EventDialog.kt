package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.rules.PhoneNumbers

/** Карточка события. Действия над номером добавляются через [actions]. */
@Composable
fun EventDialog(
    e: CallEvent,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text(PhoneNumbers.pretty(e.number)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Line("Время", fmtFull(e.timestamp))
                Line("SIM", "SIM${e.simSlot ?: "?"}" + (e.simLabel?.let { " · $it" } ?: ""))
                Line("Номер", e.rawNumber ?: "—")
                Line("Как передан", e.presentation.name)
                e.contactName?.let { Line("Контакт", it) }
                Line("Подпись (маркировка)", e.callerName?.let { n -> n + (e.labelSource?.let { " · ${it.title}" } ?: "") } ?: "—")
                Line("Решение", actionLabel(e.action) + " · " + e.source.name)
                e.outcome?.let { Line("Звонок", outcomeLabel(it)) }
                e.ruleName?.let { Line("Правило", it) }
                e.dryRun?.let { Line("Dry-run", "$it → ${actionLabel(e.wouldAction)}") }
                Line("Telegram", e.tgStatus.name)
                actions()
                e.details?.let {
                    Text("Данные системы", style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
    )
}

@Composable
private fun Line(k: String, v: String) {
    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(v, style = MaterialTheme.typography.bodyMedium)
}
