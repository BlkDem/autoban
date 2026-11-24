package com.blkdem.autoban

import android.app.Application
import android.content.Context
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.data.Settings
import com.blkdem.autoban.screening.ScreeningProcessor

class AutobanApp : Application() {

    val repository by lazy { Repository(this) }
    val settings by lazy { Settings(this) }
    val processor by lazy { ScreeningProcessor(this, repository, settings) }

    companion object {
        fun from(context: Context) = context.applicationContext as AutobanApp
    }
}
