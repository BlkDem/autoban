package com.blkdem.autoban.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.blkdem.autoban.Features

/**
 * Что приложение делает с данными — показывается при первом запуске, до запроса разрешений
 * (требование Google Play к явному раскрытию: номера уходят с телефона на zvonili.com и в Telegram).
 */
@Composable
fun DisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Ваши данные") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Para("Autoban проверяет входящие звонки по вашим правилам и ведёт журнал звонков. Журнал, правила и списки хранятся только на этом телефоне.")
                Para("• Номер звонящего не из контактов отправляется на сайт zvonili.com, чтобы найти отзывы о нём. Это можно выключить в настройках («Пробивать номер»).")
                Para(
                    "• Если вы подключите своего Telegram-бота, сведения о звонках (номер, имя контакта, подпись оператора, решение)" +
                        (if (Features.smsForwarding) " и — по отдельному переключателю — входящие SMS" else "") +
                        " отправляются в ваш чат с этим ботом через Telegram.",
                )
                Para("• Журнал вызовов, контакты и уведомления звонилки читаются только чтобы узнать подпись оператора и имя звонящего. Разрешения запрашиваются, когда вы включаете соответствующую функцию.")
                Para("Разработчик не получает никаких данных: у приложения нет своего сервера, рекламы и аналитики.")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Понятно, продолжить") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Выйти") } },
    )
}

@Composable
private fun Para(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)
