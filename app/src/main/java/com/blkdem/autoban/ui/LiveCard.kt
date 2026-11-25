package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.data.CallEvent

/** Разрешения для сопровождения звонка: подпись оператора, сброс по правилу и из Telegram. */
@Composable
fun LiveCard(
    status: SystemStatus,
    onNotificationAccess: () -> Unit,
    onAnswerCalls: () -> Unit,
    onCallLog: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Подпись оператора и сброс", fontWeight = FontWeight.SemiBold)
            Text(
                "Маркировку («Ростелеком: связь») оператор присылает, когда телефон уже звонит, — фильтр её не видит. " +
                    "Autoban читает её из уведомления звонилки и сбрасывает звонок, если её ловит правило. " +
                    "Пока телефон звонит, кнопка «Сбросить» в Telegram срабатывает сразу.",
                style = MaterialTheme.typography.bodySmall,
            )
            Line(status.notificationAccess, "Доступ к уведомлениям — видеть подпись во время звонка", "Открыть", onNotificationAccess)
            Line(status.answerCalls, "Управление звонками — сбрасывать звонок", "Разрешить", onAnswerCalls)
            Line(status.callLog, "Журнал вызовов — подпись после звонка", "Разрешить", onCallLog)
        }
    }
}

@Composable
private fun Line(ok: Boolean, text: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✅" else "⚠️")
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!ok) Button(onClick = onAction) { Text(action) }
    }
}

fun outcomeLabel(o: CallEvent.Outcome): String = when (o) {
    CallEvent.Outcome.RINGING -> "📞 звонит"
    CallEvent.Outcome.ANSWERED -> "✅ принят"
    CallEvent.Outcome.MISSED -> "📵 не отвечен"
    CallEvent.Outcome.HUNG_UP_BOT -> "⛔ сброшен из Telegram"
    CallEvent.Outcome.HUNG_UP_RULE -> "⛔ сброшен по подписи"
}
