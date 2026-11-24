package com.blkdem.autoban.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.rules.RuleMode
import com.blkdem.autoban.rules.Rules
import kotlin.concurrent.thread

@Composable
fun RulesScreen(repo: Repository, onEdit: (Rule) -> Unit = {}, header: @Composable () -> Unit = {}) {
    val changes by repo.changes.collectAsState()
    val rules = load(changes, initial = emptyList<Rule>()) { repo.rules() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        item {
            Text(
                "Проверка: белый список → бан-лист → правила сверху вниз. Срабатывает первое подходящее. " +
                    "Правила в режиме dry-run только пишут в журнал «сработало бы».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(rules, key = { it.id }) { r ->
            RuleRow(r, onClick = { onEdit(r) }, onToggle = { on -> thread { repo.saveRule(r.copy(enabled = on)) } })
            HorizontalDivider()
        }
    }
}

@Composable
fun RuleRow(r: Rule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, fontWeight = FontWeight.SemiBold)
                if (r.mode == RuleMode.DRY_RUN) Tag("dry-run", Palette.dryRun(dark))
            }
            Text(
                (if (r.conditions.isEmpty()) "любой звонок" else r.conditions.joinToString(" и ") { Rules.describe(it) }) +
                    " → " + Rules.describe(r.action),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.of(r.action, dark),
            )
            Text("приоритет ${r.priority}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = r.enabled, onCheckedChange = onToggle)
    }
}
