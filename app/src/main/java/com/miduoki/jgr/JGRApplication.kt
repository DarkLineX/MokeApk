package com.miduoki.jgr

import android.app.Application
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

lateinit var jgrApp: JGRApplication

class JGRApplication : Application() {

    lateinit var tmpApkDir: File

    override fun onCreate() {
        super.onCreate()
        jgrApp = this
        tmpApkDir = cacheDir.resolve("apk").also { it.mkdir() }
    }
}
