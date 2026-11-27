package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.telegram.TelegramClient
import com.blkdem.autoban.telegram.TelegramFormat
import com.blkdem.autoban.telegram.TelegramSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelegramCard(
    settings: TelegramSettings,
    onSaved: () -> Unit,
    onRetryFailed: () -> Unit,
    /** Запросить недостающие разрешения (определителю — контакты и журнал вызовов, SMS — приём SMS). */
    onNeedPermissions: (Array<String>) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun ask(vararg perms: String) {
        val missing = perms.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) onNeedPermissions(missing.toTypedArray())
    }
    val initial = remember { settings.snapshot() }
    var s by remember { mutableStateOf(initial) }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(settings.hasToken()) }
    var portText by remember { mutableStateOf(initial.proxy.port.toString()) }
    var digestText by remember { mutableStateOf(initial.digestMinutes.toString()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun current() = s.copy(
        token = token.ifBlank { s.token },
        proxy = s.proxy.copy(port = portText.toIntOrNull() ?: 0),
        digestMinutes = digestText.toIntOrNull() ?: 30,
    )

    fun runNet(label: String, block: (TelegramSettings.Snapshot) -> String) {
        val snap = current()
        if (snap.token.isNullOrBlank()) { status = "Сначала укажите токен"; return }
        status = "$label…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                try { block(snap) } catch (e: TelegramClient.TelegramException) { "Ошибка: ${e.message}" }
            }
        }
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Telegram", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(s.enabled, { s = s.copy(enabled = it) })
            }
            OutlinedTextField(
                token, { token = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (hasToken) "Токен бота (сохранён; ввести новый)" else "Токен бота от @BotFather") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    s.chatId, { s = s.copy(chatId = it.trim()) }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text("chat_id") },
                )
                OutlinedButton(onClick = {
                    runNet("Ищу чаты") { snap ->
                        val chats = snap.client().recentChats()
                        if (chats.isEmpty()) "Чатов не найдено: напишите боту любое сообщение и повторите"
                        else {
                            s = s.copy(chatId = chats.first().id.toString())
                            "Найдено: " + chats.joinToString { "${it.title} (${it.id})" }
                        }
                    }
                }) { Text("Найти") }
            }
            Text("О чём сообщать", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.notifyRejected, { s = s.copy(notifyRejected = !s.notifyRejected) }, label = { Text("сброс и бан") })
                FilterChip(s.notifyDryRun, { s = s.copy(notifyDryRun = !s.notifyDryRun) }, label = { Text("dry-run") })
                FilterChip(s.notifyAllowed, { s = s.copy(notifyAllowed = !s.notifyAllowed) }, label = { Text("разрешённые звонки") })
                FilterChip(s.silent, { s = s.copy(silent = !s.silent) }, label = { Text("без звука") })
                FilterChip(s.commands, { s = s.copy(commands = !s.commands) }, label = { Text("кнопки и команды") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Определитель номера", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Сообщение о каждом входящем сразу, пока телефон звонит, — с кнопками «Сбросить» и «Бан». " +
                            "Звонки от контактов тоже (с именем из контактов).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(s.callerId, {
                    s = s.copy(callerId = it)
                    if (it) ask(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_CONTACTS)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Пересылать SMS", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Входящие SMS уходят в чат с ботом — включая коды подтверждения из банков. " +
                            "Telegram хранит их у себя: включайте, только если доступ к вашему Telegram надёжно защищён.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(s.forwardSms, {
                    s = s.copy(forwardSms = it)
                    if (it) ask(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CONTACTS)
                })
            }
            Text("Как отправлять", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.mode == TelegramSettings.Mode.INSTANT, { s = s.copy(mode = TelegramSettings.Mode.INSTANT) }, label = { Text("сразу") })
                FilterChip(s.mode == TelegramSettings.Mode.DIGEST, { s = s.copy(mode = TelegramSettings.Mode.DIGEST) }, label = { Text("сводкой") })
                if (s.mode == TelegramSettings.Mode.DIGEST) {
                    OutlinedTextField(
                        digestText, { digestText = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { Text("раз в, мин") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            Text("Прокси", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelegramClient.ProxyConfig.Type.entries.forEach { t ->
                    FilterChip(s.proxy.type == t, { s = s.copy(proxy = s.proxy.copy(type = t)) },
                        label = { Text(when (t) { TelegramClient.ProxyConfig.Type.NONE -> "нет"; TelegramClient.ProxyConfig.Type.HTTP -> "HTTP"; else -> "SOCKS5" }) })
                }
            }
            if (s.proxy.type != TelegramClient.ProxyConfig.Type.NONE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.proxy.host, { s = s.copy(proxy = s.proxy.copy(host = it.trim())) }, singleLine = true,
                        label = { Text("Хост") }, modifier = Modifier.weight(2f))
                    OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, singleLine = true,
                        label = { Text("Порт") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val snap = current()
                    settings.save(snap, token.ifBlank { null })
                    if (token.isNotBlank()) hasToken = true
                    token = ""
                    s = settings.snapshot()
                    status = "Сохранено"
                    onSaved()
                }) { Text("Сохранить") }
                OutlinedButton(onClick = {
                    runNet("Отправляю") { snap ->
                        if (snap.chatId.isBlank()) "Укажите chat_id"
                        else {
                            snap.client().sendMessage(snap.chatId, "✅ <b>Autoban</b>: связь с ботом работает\n" + TelegramFormat.escape(fmtFull(System.currentTimeMillis())), silent = true)
                            "Тестовое сообщение отправлено"
                        }
                    }
                }) { Text("Тест") }
                OutlinedButton(onClick = { onRetryFailed(); status = "Неотправленные поставлены в очередь" }) { Text("Повторить ошибки") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
