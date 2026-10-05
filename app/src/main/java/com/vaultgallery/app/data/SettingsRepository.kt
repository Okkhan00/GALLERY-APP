package com.vaultgallery.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/** theme: 0 system, 1 dark, 2 light, 3 AMOLED black. layout: 0 = 1 col, 1 = 2 col, 2 = 3 col, 3 = masonry.
 *  sort: 0 newest, 1 oldest, 2 name A-Z, 3 largest, 4 name Z-A, 5 longest (videos).
 *  mediaFilter: -1 all, 0 photos, 1 videos (remembered between launches).
 *  videoSpeedPct: default playback speed as a percentage (100 = 1.0x). Nothing secret lives here (PIN verifier is in PinManager). */
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
    val mediaFilter: Int = -1,
    val videoAutoNext: Boolean = false,
    val videoSpeedPct: Int = 100,
    val videoRememberPosition: Boolean = true,
    val videoKeepAwake: Boolean = true,
    val videoShowDuration: Boolean = true,
    val timeline: Boolean = true,
    /** Vault auto-lock in seconds: 0 immediately, 30, 60, 300, -1 never. Secure default = 60. */
    val vaultAutoLockSec: Int = 60,
    /** 0 balanced, 1 performance, 2 battery saver. */
    val perfMode: Int = 0,
    val newMediaBanner: Boolean = true,
    /** Epoch ms; files added after this count as "new". 0 = not initialised yet. */
    val newSince: Long = 0L,
    val animations: Boolean = true,
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
    val MEDIA_FILTER = intPreferencesKey("media_filter")
    val VIDEO_AUTO_NEXT = booleanPreferencesKey("video_auto_next")
    val VIDEO_SPEED = intPreferencesKey("video_speed_pct")
    val VIDEO_REMEMBER_POS = booleanPreferencesKey("video_remember_pos")
    val VIDEO_KEEP_AWAKE = booleanPreferencesKey("video_keep_awake")
    val VIDEO_SHOW_DURATION = booleanPreferencesKey("video_show_duration")
    val TIMELINE = booleanPreferencesKey("timeline")
    val VAULT_AUTO_LOCK = intPreferencesKey("vault_auto_lock_sec")
    val PERF_MODE = intPreferencesKey("perf_mode")
    val NEW_BANNER = booleanPreferencesKey("new_media_banner")
    val NEW_SINCE = longPreferencesKey("new_since")
    val ANIMATIONS = booleanPreferencesKey("animations")
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
            // The old 15-minute option was dropped; map it to 5 minutes so the chip row still shows a selection.
            autoLockSec = (p[SettingsKeys.AUTO_LOCK] ?: d.autoLockSec).let { if (it > 300) 300 else it },
            secureScreens = p[SettingsKeys.SECURE] ?: d.secureScreens,
            showNames = p[SettingsKeys.SHOW_NAMES] ?: d.showNames,
            mediaFilter = p[SettingsKeys.MEDIA_FILTER] ?: d.mediaFilter,
            videoAutoNext = p[SettingsKeys.VIDEO_AUTO_NEXT] ?: d.videoAutoNext,
            videoSpeedPct = p[SettingsKeys.VIDEO_SPEED] ?: d.videoSpeedPct,
            videoRememberPosition = p[SettingsKeys.VIDEO_REMEMBER_POS] ?: d.videoRememberPosition,
            videoKeepAwake = p[SettingsKeys.VIDEO_KEEP_AWAKE] ?: d.videoKeepAwake,
            videoShowDuration = p[SettingsKeys.VIDEO_SHOW_DURATION] ?: d.videoShowDuration,
            timeline = p[SettingsKeys.TIMELINE] ?: d.timeline,
            vaultAutoLockSec = p[SettingsKeys.VAULT_AUTO_LOCK] ?: d.vaultAutoLockSec,
            perfMode = p[SettingsKeys.PERF_MODE] ?: d.perfMode,
            newMediaBanner = p[SettingsKeys.NEW_BANNER] ?: d.newMediaBanner,
            newSince = p[SettingsKeys.NEW_SINCE] ?: d.newSince,
            animations = p[SettingsKeys.ANIMATIONS] ?: d.animations,
        )
    }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.settingsStore.edit { it[key] = value }
    }
}
