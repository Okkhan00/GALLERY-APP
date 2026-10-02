package com.vaultgallery.app.domain

import androidx.room.withTransaction
import com.vaultgallery.app.BuildConfig
import com.vaultgallery.app.data.AppDatabase
import com.vaultgallery.app.data.WalletEntity
import com.vaultgallery.app.data.WalletTransactionEntity

enum class Currency { STARS, COINS }

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object NotEnough : UnlockResult
    data object NotLocked : UnlockResult
    data object Missing : UnlockResult
}

/** All balance changes go through here, inside one DB transaction, and always write a ledger row. */
class WalletRepository(private val db: AppDatabase) {
    val wallet = db.wallet().observe()

    suspend fun ensure() = db.wallet().ensure(WalletEntity(updatedAt = System.currentTimeMillis()))

    suspend fun unlock(photoId: Long, currency: Currency): UnlockResult = db.withTransaction {
        val photo = db.photos().get(photoId) ?: return@withTransaction UnlockResult.Missing
        if (!photo.locked) return@withTransaction UnlockResult.NotLocked
        val now = System.currentTimeMillis()
        val cost = if (currency == Currency.STARS) photo.unlockCostStars else photo.unlockCostCoins
        val changed = if (currency == Currency.STARS) db.wallet().spendStars(cost, now) else db.wallet().spendCoins(cost, now)
        if (changed == 0) return@withTransaction UnlockResult.NotEnough
        db.wallet().insertTx(
            WalletTransactionEntity(
                type = "UNLOCK", currency = currency.name, amount = -cost,
                reason = "Unlocked photo", photoId = photoId, timestamp = now
            )
        )
        db.photos().setLocked(photoId, false, photo.unlockCostStars, photo.unlockCostCoins, now)
        UnlockResult.Success
    }

    /** Test credits. Only callable in debug builds. */
    suspend fun debugGrant(stars: Int, coins: Int) {
        if (!BuildConfig.DEBUG) return
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.wallet().add(stars, coins, now)
            db.wallet().insertTx(WalletTransactionEntity(type = "DEBUG_GRANT", currency = "BOTH", amount = stars + coins, reason = "Debug grant", photoId = null, timestamp = now))
        }
    }
}
