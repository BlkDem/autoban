package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.telegram.BotSync
import com.blkdem.autoban.telegram.TelegramClient
import com.blkdem.autoban.telegram.TelegramSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Перенос правил и списков на другой телефон через закреплённое сообщение в чате бота. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncCard(repo: Repository, settings: TelegramSettings) {
    var status by remember { mutableStateOf<String?>(null) }
    var fetched by remember { mutableStateOf<BotSync.Fetched?>(null) }
    val scope = rememberCoroutineScope()

    fun run(label: String, block: (TelegramSettings.Snapshot) -> String?) {
        val s = settings.snapshot()
        if (!s.configured) { status = "Сначала укажите токен и chat_id в карточке Telegram и нажмите «Сохранить»"; return }
        status = "$label…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                try { block(s) } catch (e: TelegramClient.TelegramException) { "Ошибка: ${e.message}" }
            }
        }
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Перенос на другой телефон", fontWeight = FontWeight.SemiBold)
            Text(
                "Правила и списки передаются через вашего бота: этот телефон отправляет файл в чат и закрепляет его, " +
                    "другой (с тем же токеном и chat_id) забирает закреплённый файл.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { run("Отправка") { BotSync.upload(repo, it) } }) { Text("Отправить в бота") }
                OutlinedButton(onClick = { run("Загрузка") { fetched = BotSync.fetch(it); null } }) { Text("Загрузить из бота") }
            }
            Text(
                "На втором телефоне с тем же ботом выключите «кнопки и команды» — иначе телефоны будут перехватывать нажатия друг у друга.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    fetched?.let { f ->
        val b = f.backup
        fun apply(withRules: Boolean) {
            fetched = null
            scope.launch {
                status = withContext(Dispatchers.IO) {
                    val before = repo.list(ListType.BLOCK).size to repo.list(ListType.WHITE).size
                    repo.importBackup(if (withRules) b else b.copy(rules = emptyList()))
                    val after = repo.list(ListType.BLOCK).size to repo.list(ListType.WHITE).size
                    "Загружено: в бан +${after.first - before.first}, в белый +${after.second - before.second}" +
                        if (withRules) ", правила заменены (${b.rules.size})" else ""
                }
            }
        }
        AlertDialog(
            onDismissRequest = { fetched = null },
            title = { Text("Копия от ${fmtFull(f.dateMillis)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.caption?.lineSequence()?.drop(1)?.firstOrNull()?.substringBefore(" · ")?.let { Text("Телефон: $it") }
                    Text("Бан-лист: ${b.blocklist.size}, белый список: ${b.whitelist.size}, правил: ${b.rules.size}")
                    Text(
                        "Записи списков добавятся к имеющимся. «Списки и правила» заменит все правила на этом телефоне — " +
                            "проверьте потом номера SIM в условиях.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { apply(withRules = false) }) { Text("Только списки") } },
            dismissButton = {
                Column {
                    TextButton(onClick = { apply(withRules = true) }, enabled = b.rules.isNotEmpty()) { Text("Списки и правила") }
                    TextButton(onClick = { fetched = null }) { Text("Отмена") }
                }
            },
        )
    }
}
