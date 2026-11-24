package com.blkdem.autoban.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blkdem.autoban.rules.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutobanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFFFF8A80), secondary = Color(0xFF90CAF9))
        else lightColorScheme(primary = Color(0xFFC62828), secondary = Color(0xFF1565C0)),
        content = content,
    )
}

/** Загрузить данные в фоне и перезагружать при смене ключей. */
@Composable
fun <T> load(vararg keys: Any?, initial: T, block: () -> T): T {
    val v by produceState(initial, *keys) { value = withContext(Dispatchers.IO) { block() } }
    return v
}

object Palette {
    fun of(a: Action, dark: Boolean): Color = when (a) {
        Action.ALLOW -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        Action.REJECT -> if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
        Action.BAN -> if (dark) Color(0xFFE57373) else Color(0xFFC62828)
    }
    fun dryRun(dark: Boolean) = if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0)
}

fun actionLabel(a: Action) = when (a) {
    Action.ALLOW -> "пропущен"
    Action.REJECT -> "сброшен"
    Action.BAN -> "забанен"
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private val dateTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
private val dayTime = SimpleDateFormat("dd.MM HH:mm", Locale.US)
fun fmtFull(ts: Long): String = synchronized(dateTime) { dateTime.format(Date(ts)) }
fun fmtShort(ts: Long): String = synchronized(dayTime) { dayTime.format(Date(ts)) }
