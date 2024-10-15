package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

class ActivityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun saveActivityTime() {
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong("last_activity_time", currentTime).apply()
    }

    fun getLastActivityTime(): Long {
        return prefs.getLong("last_activity_time", 0)
    }
}