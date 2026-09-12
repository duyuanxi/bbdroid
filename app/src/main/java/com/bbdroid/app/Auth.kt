package com.bbdroid.app

import android.content.Context

object Auth {
    private const val PREFS = "bbdroid"
    private const val KEY_COOKIE = "bili_cookie"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COOKIE, "") ?: ""

    fun save(context: Context, cookie: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_COOKIE, cookie).commit()
    }

    fun isLoggedIn(cookie: String): Boolean = cookie.contains("SESSDATA")
}
