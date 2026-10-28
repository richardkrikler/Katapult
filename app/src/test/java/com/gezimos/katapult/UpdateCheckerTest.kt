package com.gezimos.katapult

import com.gezimos.katapult.util.UpdateChecker.isNewer
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun isNewer_ordersForkVersions() {
        val cases = listOf(
            // latest tag, installed versionName, expected
            Triple("v1.7", "1.6", true),
            Triple("v1.6-rk.1", "1.6", true),
            Triple("v1.6-rk.2", "1.6-rk.1", true),
            Triple("v1.6-rk.1", "1.6-rk.1", false),
            Triple("v1.6-rk.10", "1.6-rk.9", true),
            Triple("v1.7-rk.1", "1.6-rk.9", true),
            Triple("v1.6", "1.6-rk.1", false),
        )
        for ((latest, installed, expected) in cases) {
            assertEquals("$latest vs $installed", expected, isNewer(latest, installed))
        }
    }
}
