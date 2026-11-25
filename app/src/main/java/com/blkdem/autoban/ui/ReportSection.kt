package com.blkdem.autoban.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.lookup.NumberReport
import com.blkdem.autoban.lookup.ZvoniliParser
import com.blkdem.autoban.rules.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Цвет сводки: плохие отзывы — красным, хорошие — зелёным. */
@Composable
fun reportColor(r: NumberReport): Color {
    val dark = isSystemInDarkTheme()
    return when (r.verdict) {
        NumberReport.Verdict.BAD -> Palette.of(Action.BAN, dark)
        NumberReport.Verdict.GOOD -> Palette.of(Action.ALLOW, dark)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Отзывы о номере в карточке звонка: сводка из журнала, «Проверить»/«Обновить», ссылка на сайт. */
@Composable
fun ReportSection(eventId: Long, number: String?, repo: Repository, fetch: (String, Boolean) -> NumberReport?) {
    if (number == null || !ZvoniliParser.isRussian(number)) return
    val changes by repo.changes.collectAsState()
    val event = load(changes, eventId, initial = null as CallEvent?) { repo.event(eventId) }
    var fresh by remember { mutableStateOf<NumberReport?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val r = fresh ?: event?.report

    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Отзывы о номере", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r == null) {
            Text("Сведений пока нет", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(r.icon + " " + r.summary(maxCategories = 5), fontWeight = FontWeight.SemiBold, color = reportColor(r))
            r.where()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                (r.views?.let { "Просмотров страницы: $it · " } ?: "") + "проверено ${fmtShort(r.fetchedAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            for (c in r.comments) {
                Text(
                    listOfNotNull(c.date, c.type).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(c.text, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                status = "Ищу…"
                scope.launch {
                    val x = withContext(Dispatchers.IO) { fetch(number, r != null) }
                    if (x == null) {
                        status = "Сайт не ответил"
                    } else {
                        fresh = x
                        status = null
                    }
                }
            }) { Text(if (r == null) "Проверить номер" else "Обновить") }
            (r?.url ?: ZvoniliParser.url(number))?.let { url ->
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text(ZvoniliParser.SOURCE) }
            }
        }
    }
}

/** Настройка поиска отзывов. */
@Composable
fun LookupCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Пробивать номер", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(enabled, onChange)
            }
            Text(
                "При звонке не из контактов приложение ищет отзывы о номере на ${ZvoniliParser.SOURCE}: оценку, тип " +
                    "(мошенники, реклама…), оператора и регион. Сводка попадает в журнал и в сообщение Telegram. " +
                    "Номер при этом передаётся сайту. Звонок не задерживается, если нет боевых правил по репутации.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
