package com.b2tech.fimirtsp

import android.app.Application
import com.b2tech.fimirtsp.settings.SettingsRepository

class FimiApp : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    companion object {
        lateinit var instance: FimiApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
