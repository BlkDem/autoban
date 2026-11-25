package com.blkdem.autoban.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import com.blkdem.autoban.rules.CallLabel
import com.blkdem.autoban.rules.PhoneNumbers

/**
 * Подпись оператора из журнала вызовов. Звонилка Samsung сохраняет маркировку («Ростелеком: связь»)
 * вместе со звонком; колонка у разных прошивок своя, поэтому смотрим все текстовые поля записи.
 */
object CallLogReader {
    private const val TAG = "Autoban"

    /** Колонки, где подпись вероятнее всего; остальные текстовые — запасной вариант. */
    private val PREFERRED = listOf("cnap_name", "cnap", CallLog.Calls.CACHED_NAME, "caller_name", "name")

    fun label(context: Context, number: String?, since: Long): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, "${CallLog.Calls.DATE} >= ?", arrayOf((since - 60_000).toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c -> find(c, number) }
        } catch (e: Exception) {
            Log.w(TAG, "call log read failed: $e")
            null
        }
    }

    private fun find(c: Cursor, number: String?): String? {
        val numberCol = c.getColumnIndex(CallLog.Calls.NUMBER)
        var rows = 0
        while (c.moveToNext() && rows++ < 10) {
            if (number != null && numberCol >= 0 && PhoneNumbers.normalize(c.getString(numberCol)) != number) continue
            val texts = LinkedHashMap<String, String>()
            for (i in 0 until c.columnCount) {
                if (c.getType(i) != Cursor.FIELD_TYPE_STRING) continue
                val v = c.getString(i)?.trim()?.takeIf { it.isNotEmpty() && "//" !in it } ?: continue
                texts[c.getColumnName(i)] = v
            }
            val ordered = PREFERRED.mapNotNull { texts[it] } + texts.values
            val label = CallLabel.fromTexts(ordered)
            // Имена колонок с текстом — чтобы на новой прошивке понять, где лежит подпись.
            Log.i(TAG, "call log entry: text columns ${texts.keys} -> label=${label != null}")
            return label
        }
        return null
    }
}
