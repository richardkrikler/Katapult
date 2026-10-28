package com.gezimos.katapult.lockscreen

import android.app.KeyguardManager
import android.app.Notification
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.provider.Settings

/**
 * What the lock screen may reveal about a notification, following SystemUI's rules
 * (NotificationLockscreenUserManagerImpl) through public APIs only.
 */
object LockscreenPrivacy {
    enum class Visibility { SHOW, REDACT, HIDE }

    /** NotificationChannel.lockscreenVisibility when the user hasn't changed it (hidden NotificationManager.VISIBILITY_NO_OVERRIDE). */
    const val NO_OVERRIDE = -1000

    data class DeviceState(
        val keyguardSecure: Boolean,
        val showOnLockscreen: Boolean,
        val allowPrivate: Boolean,
        val keyguardDisabledFeatures: Int,
    )

    // Settings.Secure keys behind "Notifications on lock screen" / "Sensitive notifications".
    private const val SHOW_NOTIFICATIONS = "lock_screen_show_notifications"
    private const val ALLOW_PRIVATE = "lock_screen_allow_private_notifications"

    /** Read fresh on each use: the user may change these while the widget is up. */
    fun read(context: Context): DeviceState {
        val resolver = context.contentResolver

        // A setting that can't be read counts as its restrictive value.
        fun secureFlag(name: String) =
            try {
                Settings.Secure.getInt(resolver, name, 1) != 0
            } catch (_: Exception) {
                false
            }
        return DeviceState(
            keyguardSecure = try {
                context.getSystemService(KeyguardManager::class.java).isKeyguardSecure
            } catch (_: Exception) {
                true
            },
            showOnLockscreen = secureFlag(SHOW_NOTIFICATIONS),
            allowPrivate = secureFlag(ALLOW_PRIVATE),
            keyguardDisabledFeatures = try {
                context.getSystemService(DevicePolicyManager::class.java).getKeyguardDisabledFeatures(null)
            } catch (_: Exception) {
                0
            },
        )
    }

    /**
     * [notificationVisibility] is Notification.visibility; [channelOverride] is the user's
     * per-channel choice from the ranking ([NO_OVERRIDE] if none).
     */
    fun decide(
        device: DeviceState,
        notificationVisibility: Int,
        channelOverride: Int,
    ): Visibility {
        // A swipe-only lock screen protects nothing, so stock shows everything. (With no
        // PIN, MuditaOS has no lock screen at all and the widget never shows.)
        if (!device.keyguardSecure) return Visibility.SHOW
        if (!device.showOnLockscreen ||
            device.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS != 0
        ) {
            return Visibility.HIDE
        }
        val overridden = channelOverride != NO_OVERRIDE
        val visibility = if (overridden) channelOverride else notificationVisibility
        return when {
            visibility == Notification.VISIBILITY_SECRET -> Visibility.HIDE
            visibility == Notification.VISIBILITY_PUBLIC -> Visibility.SHOW
            !device.allowPrivate -> Visibility.REDACT
            device.keyguardDisabledFeatures and DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS != 0 -> Visibility.REDACT
            overridden -> Visibility.REDACT // the user explicitly chose PRIVATE for this channel
            else -> Visibility.SHOW
        }
    }
}
