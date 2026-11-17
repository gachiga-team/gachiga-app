package com.gachiga.app.data.trip

import android.content.SharedPreferences

/** 앱이 꺼졌다 켜져도 남아야 하는 시각 하나를 담는다 (예: 정차를 시작한 시각). */
interface LongStore {
    fun get(): Long
    fun set(value: Long)

    class InMemory(private var value: Long = 0) : LongStore {
        override fun get() = value
        override fun set(value: Long) { this.value = value }
    }

    class Prefs(private val prefs: SharedPreferences, private val key: String) : LongStore {
        override fun get() = prefs.getLong(key, 0)
        override fun set(value: Long) {
            if (prefs.getLong(key, 0) != value) prefs.edit().putLong(key, value).apply()
        }
    }
}
