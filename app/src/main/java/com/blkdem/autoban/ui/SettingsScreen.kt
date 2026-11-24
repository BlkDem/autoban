package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Состояние системных разрешений, которое показывает экран настроек. */
data class SystemStatus(
    val roleSupported: Boolean,
    val roleHeld: Boolean,
    val phoneStateGranted: Boolean,
)

@Composable
fun StatusCard(
    status: SystemStatus,
    protectionEnabled: Boolean,
    onRequestRole: () -> Unit,
    onRequestPhoneState: () -> Unit,
    onProtection: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Защита", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (protectionEnabled) "Правила применяются" else "Выключена: звонки только журналируются",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = protectionEnabled, onCheckedChange = onProtection)
            }
            StatusLine(
                ok = status.roleHeld,
                text = when {
                    !status.roleSupported -> "Роль «Фильтр звонков» не поддерживается (нужен Android 10+)"
                    status.roleHeld -> "Роль «Фильтр звонков» назначена"
                    else -> "Роль «Фильтр звонков» не назначена — звонки не проверяются"
                },
                action = if (!status.roleHeld && status.roleSupported) "Назначить" else null,
                onAction = onRequestRole,
            )
            StatusLine(
                ok = status.phoneStateGranted,
                text = if (status.phoneStateGranted) "Доступ к SIM есть" else "Нет доступа к SIM — правила по SIM не сработают",
                action = if (!status.phoneStateGranted) "Разрешить" else null,
                onAction = onRequestPhoneState,
            )
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✅" else "⚠️")
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (action != null) {
            if (ok) OutlinedButton(onClick = onAction) { Text(action) } else Button(onClick = onAction) { Text(action) }
        }
    }
}
