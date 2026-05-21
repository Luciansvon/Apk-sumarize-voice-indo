package com.sumarize.voiceindo

import android.app.Application
import com.sumarize.voiceindo.data.db.AppDatabase

class SumarizeApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
