package com.gezimos.katapult

import android.app.Notification.VISIBILITY_PRIVATE
import android.app.Notification.VISIBILITY_PUBLIC
import android.app.Notification.VISIBILITY_SECRET
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.NO_OVERRIDE
import android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS
import android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.DeviceState
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.Visibility.HIDE
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.Visibility.REDACT
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.Visibility.SHOW
import com.gezimos.katapult.lockscreen.LockscreenPrivacy.decide
import org.junit.Assert.assertEquals
import org.junit.Test

class LockscreenPrivacyTest {

    private val open = DeviceState(keyguardSecure = true, showOnLockscreen = true, allowPrivate = true, keyguardDisabledFeatures = 0)

    @Test
    fun decide_followsSystemUiRules() {
        val cases = listOf(
            // device, notification visibility, channel override, expected
            Triple(open.copy(keyguardSecure = false, showOnLockscreen = false), VISIBILITY_SECRET to NO_OVERRIDE, SHOW),
            Triple(open.copy(showOnLockscreen = false), VISIBILITY_PUBLIC to NO_OVERRIDE, HIDE),
            Triple(open.copy(keyguardDisabledFeatures = KEYGUARD_DISABLE_SECURE_NOTIFICATIONS), VISIBILITY_PUBLIC to NO_OVERRIDE, HIDE),
            Triple(open, VISIBILITY_SECRET to NO_OVERRIDE, HIDE),
            Triple(open, VISIBILITY_PUBLIC to VISIBILITY_SECRET, HIDE),
            Triple(open, VISIBILITY_PUBLIC to NO_OVERRIDE, SHOW),
            Triple(open.copy(allowPrivate = false), VISIBILITY_PUBLIC to NO_OVERRIDE, SHOW),
            Triple(open.copy(allowPrivate = false), VISIBILITY_PRIVATE to VISIBILITY_PUBLIC, SHOW),
            Triple(open, VISIBILITY_PRIVATE to NO_OVERRIDE, SHOW),
            Triple(open.copy(allowPrivate = false), VISIBILITY_PRIVATE to NO_OVERRIDE, REDACT),
            Triple(open.copy(keyguardDisabledFeatures = KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS), VISIBILITY_PRIVATE to NO_OVERRIDE, REDACT),
            Triple(open, VISIBILITY_PUBLIC to VISIBILITY_PRIVATE, REDACT),
        )
        for ((device, vis, expected) in cases) {
            assertEquals("$device vis=${vis.first} override=${vis.second}", expected, decide(device, vis.first, vis.second))
        }
    }
}
