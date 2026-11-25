package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.ListImport
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.Mask
import com.blkdem.autoban.rules.PhoneNumbers
import kotlin.concurrent.thread

@Composable
fun ListsScreen(repo: Repository, onImport: (ListType) -> Unit) {
    val changes by repo.changes.collectAsState()
    var type by rememberSaveable { mutableStateOf(ListType.BLOCK) }
    val entries = load(changes, type, initial = emptyList<ListEntry>()) { repo.list(type) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = type.ordinal) {
            Tab(type == ListType.BLOCK, { type = ListType.BLOCK }, text = { Text("Бан-лист") })
            Tab(type == ListType.WHITE, { type = ListType.WHITE }, text = { Text("Белый список") })
        }
        AddEntry(type) { pattern, note -> thread { repo.addToList(type, pattern, note) } }
        Text(
            if (type == ListType.BLOCK) "Номера отсюда сбрасываются на любой SIM. Можно маски: +7495*, 8800*."
            else "Номера отсюда всегда пропускаются — проверяются раньше всех правил.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onImport(type) }) { Text("Импорт из файла") }
            Text(
                "   Записей: ${entries.size}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entries.isEmpty()) Text("Список пуст.", modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (e.pattern.startsWith("+")) PhoneNumbers.pretty(e.pattern) else e.pattern, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(fmtShort(e.createdAt), e.ruleName, e.note).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { thread { repo.removeFromList(e.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Удалить") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AddEntry(type: ListType, onAdd: (String, String?) -> Unit) {
    var pattern by remember(type) { mutableStateOf("") }
    var note by remember(type) { mutableStateOf("") }
    val value = ListImport.canonical(pattern)
    val ok = value != null
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(pattern, { pattern = it }, label = { Text("Номер или маска") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(enabled = ok, onClick = { onAdd(value!!, note.ifBlank { null }); pattern = ""; note = "" }) { Text("Добавить") }
        }
    }
}
