package com.blkdem.autoban

import android.app.Application
import android.content.Context
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.data.Settings
import com.blkdem.autoban.lookup.NumberLookup
import com.blkdem.autoban.screening.ScreeningProcessor
import com.blkdem.autoban.telegram.TelegramNotifier
import com.blkdem.autoban.telegram.TelegramSettings

class AutobanApp : Application() {

    val repository by lazy { Repository(this) }
    val settings by lazy { Settings(this) }
    val telegramSettings by lazy { TelegramSettings(settings) }
    val telegram by lazy { TelegramNotifier(this, repository, telegramSettings) }
    val lookup by lazy { NumberLookup(repository) }
    val processor by lazy {
        ScreeningProcessor(this, repository, settings, lookup).also { p -> p.addListener { telegram.onEvent(it) } }
    }

    companion object {
        fun from(context: Context) = context.applicationContext as AutobanApp
    }
}
