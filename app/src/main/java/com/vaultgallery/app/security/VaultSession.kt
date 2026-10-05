package com.vaultgallery.app.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory only: whether the vault was authenticated recently enough. Nothing about it is persisted, so a process
 * restart always requires authentication again.
 * [lastActivity] = last authentication or the last time the vault UI / app left the foreground. Auto-lock compares
 * against it when the vault is entered again, when the app returns from background, or before a "Hide" action.
 */
object VaultSession {
    private var open = false
    private var lastActivity = 0L

    fun unlock() { open = true; lastActivity = SystemClock.elapsedRealtime() }
    fun lock() { open = false }
    fun left() { if (open) lastActivity = SystemClock.elapsedRealtime() }

    /** timeoutSec: 0 = immediately, -1 = never. */
    fun isOpen(timeoutSec: Int): Boolean {
        if (!open) return false
        if (timeoutSec < 0) return true
        val away = SystemClock.elapsedRealtime() - lastActivity
        if (away >= timeoutSec * 1000L) { open = false; return false }
        return true
    }
}

/** Counts screens that must block screenshots / recents thumbnails (the vault). MainActivity applies FLAG_SECURE. */
object SecureFlag {
    private val depth = MutableStateFlow(0)
    val active: StateFlow<Int> = depth
    fun enter() { depth.update { it + 1 } }
    fun exit() { depth.update { (it - 1).coerceAtLeast(0) } }
}
