package com.blkdem.autoban.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.Rules

/** Тестер правил: вымышленный звонок → какое решение примет движок (без последствий). */
@Composable
fun TesterDialog(simulate: (CallInfo) -> Decision, onDismiss: () -> Unit) {
    var sim by remember { mutableStateOf(1) }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val info = CallInfo(
        timestamp = System.currentTimeMillis(),
        rawNumber = number.ifBlank { null },
        presentation = if (number.isBlank()) CallInfo.Presentation.HIDDEN else CallInfo.Presentation.PRESENTED,
        callerName = name.ifBlank { null },
        callerNamePresentation = if (name.isBlank()) CallInfo.Presentation.UNKNOWN else CallInfo.Presentation.PRESENTED,
        simSlot = sim,
    )
    val d = load(sim, number, name, initial = null as Decision?) { simulate(info) }
    val dark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Проверка правил") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..2).forEach { s -> FilterChip(sim == s, { sim = s }, label = { Text("SIM$s") }) }
                }
                OutlinedTextField(number, { number = it }, label = { Text("Номер (пусто — скрыт)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Имя от сети") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (d != null) {
                    Text(
                        "Итог: ${actionLabel(d.action)}",
                        fontWeight = FontWeight.SemiBold, color = Palette.of(d.action, dark),
                    )
                    Text(
                        when (d.source) {
                            Decision.Source.DEFAULT -> "Ни одно активное правило не подошло"
                            Decision.Source.DISABLED -> "Защита выключена (сработало бы: ${d.ruleName})"
                            else -> d.ruleName ?: ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (d.dryRunHits.isNotEmpty()) {
                        Text(
                            "🧪 Dry-run: " + d.dryRunHits.joinToString("; ") { "${it.name} → ${Rules.describe(it.action)}" },
                            style = MaterialTheme.typography.bodySmall, color = Palette.dryRun(dark),
                        )
                    }
                }
            }
        },
    )
}
