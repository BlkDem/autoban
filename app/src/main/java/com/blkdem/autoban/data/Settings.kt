package com.blkdem.autoban.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Простые настройки приложения в SharedPreferences. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes
    private fun changed() { _changes.value = _changes.value + 1 }

    var protectionEnabled: Boolean
        get() = prefs.getBoolean("protection", true)
        set(v) { prefs.edit().putBoolean("protection", v).apply(); changed() }

    /** Сколько дней хранить журнал (0 — бессрочно). */
    var retentionDays: Int
        get() = prefs.getInt("retention_days", 90)
        set(v) { prefs.edit().putInt("retention_days", v).apply(); changed() }

    var lastCleanup: Long
        get() = prefs.getLong("last_cleanup", 0)
        set(v) { prefs.edit().putLong("last_cleanup", v).apply() }

    internal val raw get() = prefs
    internal fun notifyChanged() = changed()
}
