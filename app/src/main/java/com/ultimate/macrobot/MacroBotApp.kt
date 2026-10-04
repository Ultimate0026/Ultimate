// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

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
