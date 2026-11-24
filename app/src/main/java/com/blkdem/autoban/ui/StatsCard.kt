package com.blkdem.autoban.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.Action
import java.util.Calendar

/** Сводка: сегодня и за 7 дней. */
@Composable
fun StatsCard(repo: Repository) {
    val changes by repo.changes.collectAsState()
    val startOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val today = load(changes, startOfDay, initial = null as Repository.Stats?) { repo.stats(startOfDay) }
    val week = load(changes, startOfDay, initial = null as Repository.Stats?) { repo.stats(startOfDay - 6 * 86_400_000L) }
    if (today == null || week == null || week.total == 0) return
    val dark = isSystemInDarkTheme()

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Text("Сегодня", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Tile(today.banned, "бан", Palette.of(Action.BAN, dark))
                Tile(today.rejected, "сброс", Palette.of(Action.REJECT, dark))
                Tile(today.allowed, "пропущ.", Palette.of(Action.ALLOW, dark))
            }
            Text(
                "За 7 дней: ${week.total} звонков · бан ${week.banned} · сброс ${week.rejected} · " +
                    "SIM1 ${week.sim1} / SIM2 ${week.sim2}" + (if (week.dryRun > 0) " · dry-run ${week.dryRun}" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tile(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
