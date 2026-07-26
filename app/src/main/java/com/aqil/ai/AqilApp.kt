package com.aqil.ai

import android.app.Application
import com.aqil.ai.data.SettingsRepository

class AqilApp : Application() {
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
    }

    companion object {
        lateinit var instance: AqilApp
            private set
    }
}
