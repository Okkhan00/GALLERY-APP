package com.vaultgallery.app.domain

/** Same price tables as the web version, made deterministic per photo id.
 *  Stars/Coins are virtual local credits with no monetary value. */
object WalletPricing {
    private val starPrices = intArrayOf(5, 10, 15, 20, 25, 30)
    private val coinPrices = intArrayOf(50, 100, 150, 200, 250, 300, 400, 500)

    fun stars(photoId: Long): Int = starPrices[Math.floorMod(photoId, starPrices.size.toLong()).toInt()]
    fun coins(photoId: Long): Int = coinPrices[Math.floorMod(photoId, coinPrices.size.toLong()).toInt()]
}
