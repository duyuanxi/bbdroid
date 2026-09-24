package com.bbdroid.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bbdroid_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glassEnabled: Boolean = false,
    val glassIntensity: Float = 0.5f,
    val saveDir: String = "",
    val concurrentDownloads: Int = 2,
    val wifiOnly: Boolean = false,
)

object SettingsStore {
    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_GLASS = booleanPreferencesKey("glass_enabled")
    private val KEY_GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
    private val KEY_SAVE_DIR = stringPreferencesKey("save_dir")
    private val KEY_CONCURRENT = intPreferencesKey("concurrent_downloads")
    private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")

    fun settings(context: Context): Flow<AppSettings> =
        context.dataStore.data.map { p ->
            AppSettings(
                themeMode = runCatching { ThemeMode.valueOf(p[KEY_THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                glassEnabled = p[KEY_GLASS] ?: false,
                glassIntensity = p[KEY_GLASS_INTENSITY] ?: 0.5f,
                saveDir = p[KEY_SAVE_DIR] ?: "",
                concurrentDownloads = p[KEY_CONCURRENT] ?: 2,
                wifiOnly = p[KEY_WIFI_ONLY] ?: false,
            )
        }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setGlassEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_GLASS] = enabled }
    }

    suspend fun setGlassIntensity(context: Context, value: Float) {
        context.dataStore.edit { it[KEY_GLASS_INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setSaveDir(context: Context, uri: String) {
        context.dataStore.edit { it[KEY_SAVE_DIR] = uri }
    }

    suspend fun setConcurrentDownloads(context: Context, n: Int) {
        context.dataStore.edit { it[KEY_CONCURRENT] = n.coerceIn(1, 8) }
    }

    suspend fun setWifiOnly(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    // 一次性读取保存目录（供同步保存逻辑用）
    suspend fun saveDir(context: Context): String =
        context.dataStore.data.first()[KEY_SAVE_DIR] ?: ""
}
