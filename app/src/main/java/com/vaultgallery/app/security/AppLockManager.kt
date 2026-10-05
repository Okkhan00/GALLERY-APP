package com.vaultgallery.app.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppLockManager {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked

    private var stoppedAt = 0L
    /** Set right before launching a system UI (permission, delete confirm, share) so returning doesn't relock. */
    @Volatile var skipNextLock = false

    fun unlock() { _locked.value = false }
    fun lock() { _locked.value = true }
    fun onStop() { stoppedAt = SystemClock.elapsedRealtime() }

    fun onStart(enabled: Boolean, timeoutSec: Int) {
        val away = if (stoppedAt == 0L) 0 else SystemClock.elapsedRealtime() - stoppedAt
        stoppedAt = 0
        if (skipNextLock) { skipNextLock = false; return }
        if (enabled && timeoutSec >= 0 && away >= timeoutSec * 1000L && away > 0) lock()
    }
}
