package com.blkdem.autoban.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.blkdem.autoban.rules.CallLabel

/**
 * Подпись оператора из системного лога. Телефонный модуль Samsung пишет её, как только пришёл звонок, —
 * раньше, чем звонок попадает в фильтр, которому Android 10 подпись не передаёт:
 * `V/Telephony: GsmConnection: updateAddress, caller display name : ООО ВЕКТОР: прочие услуги, present : 1`.
 *
 * Читать чужой лог можно только с разрешением READ_LOGS. Приложению его не выдать — только один раз через adb:
 * [GRANT_COMMAND].
 */
object RadioLog {
    private const val TAG = "Autoban"
    const val GRANT_COMMAND = "adb shell pm grant com.blkdem.autoban android.permission.READ_LOGS"

    fun available(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Последняя подпись звонка, записанная в лог не раньше [since] (мс). null — подписи нет или лог недоступен. */
    fun label(context: Context, since: Long): String? {
        if (!available(context)) return null
        return try {
            val p = ProcessBuilder(
                "logcat", "-d", "-b", "main", "-v", "epoch",
                "-T", "%d.%03d".format(since / 1000, since % 1000), "Telephony:V", "*:S",
            ).redirectErrorStream(true).start()
            val label = p.inputStream.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
            p.destroy()
            label
        } catch (e: Exception) {
            Log.w(TAG, "radio log read failed: $e")
            null
        }
    }

    private const val MARK = "caller display name : "

    /** Подпись из строк лога: последняя непустая, похожая на маркировку оператора. Чистый Kotlin — покрыт тестами. */
    fun parse(lines: Sequence<String>): String? {
        var found: String? = null
        for (line in lines) {
            val i = line.indexOf(MARK)
            if (i < 0) continue
            val name = line.substring(i + MARK.length).substringBeforeLast(", present :").trim()
            if (name.isNotEmpty() && name != "null" && CallLabel.isOperatorLabel(name)) found = name
        }
        return found
    }
}
