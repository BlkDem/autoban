package com.blkdem.autoban.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.Condition
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.PhoneNumbers
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.rules.RuleMode
import kotlin.concurrent.thread

/** Кнопки действий над номером в карточке события. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventActions(e: CallEvent, repo: Repository, onCreateRule: (Rule) -> Unit) {
    val changes by repo.changes.collectAsState()
    val number = e.number
    val inBlock = load(changes, number, initial = false) { number != null && repo.isInList(ListType.BLOCK, number) }
    val inWhite = load(changes, number, initial = false) { number != null && repo.isInList(ListType.WHITE, number) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (number != null) {
            if (inBlock) {
                OutlinedButton(onClick = { thread { repo.removeNumberFromList(ListType.BLOCK, number) } }) { Text("Разбанить") }
            } else {
                OutlinedButton(onClick = { thread { repo.addToList(ListType.BLOCK, number, e.callerName, "вручную") } }) { Text("В бан") }
            }
            if (inWhite) {
                OutlinedButton(onClick = { thread { repo.removeNumberFromList(ListType.WHITE, number) } }) { Text("Из белого списка") }
            } else {
                OutlinedButton(onClick = { thread { repo.addToList(ListType.WHITE, number, e.callerName, "вручную") } }) { Text("В белый список") }
            }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(number)) }) { Text("Копировать") }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PhoneNumbers.lookupUrl(number)))) }
                    .onFailure { Toast.makeText(context, "Нет браузера", Toast.LENGTH_SHORT).show() }
            }) { Text("Найти в интернете") }
        }
        OutlinedButton(onClick = { onCreateRule(ruleFromEvent(e)) }) { Text("Правило по звонку") }
    }
}

/** Черновик правила по звонку: та же SIM + то, чем звонок отличается (скрыт / имя / номер). Создаётся в dry-run. */
fun ruleFromEvent(e: CallEvent): Rule {
    val conditions = buildList {
        e.simSlot?.let { add(Condition.SimSlot(it)) }
        when {
            e.number == null -> add(Condition.NumberHidden)
            !e.callerName.isNullOrBlank() -> add(Condition.CallerName("*${e.callerName.trim()}*"))
            else -> add(Condition.NumberMask(e.number))
        }
    }
    val title = e.callerName ?: PhoneNumbers.pretty(e.number)
    return Rule(name = "По звонку: $title", mode = RuleMode.DRY_RUN, conditions = conditions, action = Action.REJECT)
}
