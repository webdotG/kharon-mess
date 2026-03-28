package com.kharon.messenger

import android.app.Application
import android.os.StrictMode
import net.sqlcipher.database.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KharonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализируем SQLCipher нативные библиотеки заранее
        // Без этого первый запрос к БД блокирует UI thread
        SQLiteDatabase.loadLibs(this)
        if (isRooted()) {
            Log.w("Kharon", "[!] WARNING: Device appears to be rooted. Key security may be compromised.")
        }
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/su/bin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/su", "/data/local/bin/su", "/sbin/su"
        )
        return paths.any { java.io.File(it).exists() }
    }
}
