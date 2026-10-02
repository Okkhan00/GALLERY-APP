package com.vaultgallery.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** theme: 0 system, 1 dark, 2 light. layout: 0 = 1 col, 1 = 2 col, 2 = 3 col, 3 = masonry.
 *  sort: 0 newest, 1 oldest, 2 name, 3 largest. Nothing secret lives here (PIN verifier is in PinManager). */
data class AppSettings(
    val theme: Int = 0,
    val layout: Int = 1,
    val sort: Int = 0,
    val slideshowSec: Int = 3,
    val appLock: Boolean = false,
    val biometric: Boolean = false,
    val autoLockSec: Int = 60,
    val secureScreens: Boolean = false,
    val showNames: Boolean = false,
)

object SettingsKeys {
    val THEME = intPreferencesKey("theme")
    val LAYOUT = intPreferencesKey("layout")
    val SORT = intPreferencesKey("sort")
    val SLIDESHOW = intPreferencesKey("slideshow_sec")
    val APP_LOCK = booleanPreferencesKey("app_lock")
    val BIOMETRIC = booleanPreferencesKey("biometric")
    val AUTO_LOCK = intPreferencesKey("auto_lock_sec")
    val SECURE = booleanPreferencesKey("secure_screens")
    val SHOW_NAMES = booleanPreferencesKey("show_names")
}

class SettingsRepository(private val context: Context) {
    val flow: Flow<AppSettings> = context.settingsStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[SettingsKeys.THEME] ?: d.theme,
            layout = p[SettingsKeys.LAYOUT] ?: d.layout,
            sort = p[SettingsKeys.SORT] ?: d.sort,
            slideshowSec = p[SettingsKeys.SLIDESHOW] ?: d.slideshowSec,
            appLock = p[SettingsKeys.APP_LOCK] ?: d.appLock,
            biometric = p[SettingsKeys.BIOMETRIC] ?: d.biometric,
            autoLockSec = p[SettingsKeys.AUTO_LOCK] ?: d.autoLockSec,
            secureScreens = p[SettingsKeys.SECURE] ?: d.secureScreens,
            showNames = p[SettingsKeys.SHOW_NAMES] ?: d.showNames,
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.settingsStore.edit { it[key] = value }
    }
}
