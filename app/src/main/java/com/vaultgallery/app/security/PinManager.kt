package com.vaultgallery.app.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The raw PIN is never stored or logged. We keep PBKDF2(salt, pin) sealed with a Keystore key,
 * so the verifier can't be brute-forced offline from a copied prefs file. Repeated failures lock out with backoff.
 */
class PinManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sec_prefs", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains(KEY_BLOB)

    fun setPin(pin: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val sealed = KeystoreCrypto.encrypt(ALIAS, salt + derive(pin, salt))
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
        pin.fill('0')
    }

    fun verify(pin: CharArray): Boolean {
        if (lockoutRemainingMs() > 0) { pin.fill('0'); return false }
        val ok = try {
            val blob = Base64.decode(prefs.getString(KEY_BLOB, null) ?: return false, Base64.NO_WRAP)
            val plain = KeystoreCrypto.decrypt(ALIAS, blob)
            val salt = plain.copyOfRange(0, 16)
            val expected = plain.copyOfRange(16, plain.size)
            MessageDigest.isEqual(derive(pin, salt), expected)
        } catch (e: Exception) { false }
        pin.fill('0')
        val e = prefs.edit()
        if (ok) e.putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0)
        else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            e.putInt(KEY_FAILS, fails)
            if (fails >= 5) {
                val delaySec = minOf(30L shl (fails - 5).coerceAtMost(5), 900L)
                e.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + delaySec * 1000)
            }
        }
        e.apply()
        return ok
    }

    fun lockoutRemainingMs(): Long = (prefs.getLong(KEY_LOCK_UNTIL, 0) - System.currentTimeMillis()).coerceAtLeast(0)

    fun clear() { prefs.edit().clear().apply() }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin, salt, 120_000, 256)).encoded

    private companion object {
        const val ALIAS = "vault_pin_key"
        const val KEY_BLOB = "pin_blob"
        const val KEY_FAILS = "pin_fails"
        const val KEY_LOCK_UNTIL = "pin_lock_until"
    }
}
