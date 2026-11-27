package com.blkdem.autoban

/** Что есть в этой сборке (см. productFlavors в app/build.gradle.kts). */
object Features {
    /** Пересылка SMS в Telegram — только в сборке для GitHub. */
    val smsForwarding: Boolean get() = !BuildConfig.PLAY

    /** Подпись из системного лога (READ_LOGS через adb) — только в сборке для GitHub. */
    val radioLog: Boolean get() = !BuildConfig.PLAY
}
