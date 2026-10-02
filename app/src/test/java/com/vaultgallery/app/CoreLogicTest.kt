package com.vaultgallery.app

import com.vaultgallery.app.domain.WalletPricing
import com.vaultgallery.app.util.EditMath
import com.vaultgallery.app.util.EditParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    @Test fun defaultEditIsIdentity() {
        val v = EditMath.values(EditParams())
        val id = EditMath.identity()
        for (i in v.indices) assertEquals(id[i], v[i], 1e-4f)
    }

    @Test fun blackAndWhiteHasNoColorSpread() {
        val v = EditMath.values(EditParams(saturate = 0))
        // With zero saturation all three output rows must be identical.
        for (j in 0 until 5) { assertEquals(v[j], v[5 + j], 1e-4f); assertEquals(v[j], v[10 + j], 1e-4f) }
    }

    @Test fun pricingIsDeterministicAndPositive() {
        for (id in 0L..200L) {
            assertEquals(WalletPricing.stars(id), WalletPricing.stars(id))
            assertTrue(WalletPricing.stars(id) > 0 && WalletPricing.coins(id) > 0)
        }
    }
}
