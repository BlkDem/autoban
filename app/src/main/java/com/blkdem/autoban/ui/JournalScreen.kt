package com.blkdem.autoban.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.PhoneNumbers

private enum class JournalFilter(val label: String) { ALL("Все"), REJECTED("Сброшены"), BANNED("Забанены"), ALLOWED("Пропущены"), DRY("Dry-run") }

@Composable
fun JournalScreen(repo: Repository, onOpen: (CallEvent) -> Unit) {
    val changes by repo.changes.collectAsState()
    var filter by rememberSaveable { mutableStateOf(JournalFilter.ALL) }
    var sim by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val f = Repository.EventFilter(
        actions = when (filter) {
            JournalFilter.REJECTED -> setOf(Action.REJECT)
            JournalFilter.BANNED -> setOf(Action.BAN)
            JournalFilter.ALLOWED -> setOf(Action.ALLOW)
            else -> emptySet()
        },
        onlyDryRun = filter == JournalFilter.DRY,
        simSlot = sim,
        query = query,
    )
    val events = load(changes, f, initial = emptyList<CallEvent>()) { repo.events(f) }
    // Новый звонок вставляется сверху. LazyColumn держит позицию на прежнем первом элементе,
    // и новый оказался бы за краем экрана — если смотрим верх журнала, показываем его.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val topId = events.firstOrNull()?.id
    androidx.compose.runtime.LaunchedEffect(topId) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Поиск: номер, имя, правило") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JournalFilter.entries.forEach { FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it.label) }) }
            Spacer(Modifier.width(8.dp))
            listOf(1, 2).forEach { s ->
                FilterChip(selected = sim == s, onClick = { sim = if (sim == s) null else s }, label = { Text("SIM$s") })
            }
        }
        if (events.isEmpty()) {
            Text(
                "Записей нет. Здесь появятся входящие звонки не из контактов.",
                modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(events, key = { it.id }) { e ->
                EventRow(e, Modifier.clickable { onOpen(e) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun EventRow(e: CallEvent, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(PhoneNumbers.pretty(e.number), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Tag(actionLabel(e.action), Palette.of(e.action, dark))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fmtShort(e.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("SIM${e.simSlot ?: "?"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            e.contactName?.let { Text("👤 $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            e.callerName?.let { Text("«$it»", style = MaterialTheme.typography.bodySmall) }
            e.outcome?.takeIf { it != com.blkdem.autoban.data.CallEvent.Outcome.RINGING || System.currentTimeMillis() - e.timestamp < 180_000 }
                ?.let { Text(outcomeLabel(it), style = MaterialTheme.typography.bodySmall) }
        }
        e.report?.takeIf { it.found }?.let { r ->
            Text(r.icon + " " + r.summary(maxCategories = 2), style = MaterialTheme.typography.bodySmall, color = reportColor(r))
        }
        e.ruleName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        e.dryRun?.let {
            Text(
                "🧪 сработало бы: $it → ${actionLabel(e.wouldAction)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.dryRun(dark),
            )
        }
    }
}
