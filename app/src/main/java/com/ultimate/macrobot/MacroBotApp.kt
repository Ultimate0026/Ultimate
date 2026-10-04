package com.ultimate.macrobot

import android.app.Application
import com.ultimate.macrobot.data.MacroRepository

class MacroBotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        repo = MacroRepository(this)
    }

    companion object {
        lateinit var repo: MacroRepository
            private set
    }
}
