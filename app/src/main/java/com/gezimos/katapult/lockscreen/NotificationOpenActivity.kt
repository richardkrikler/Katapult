package com.gezimos.katapult.lockscreen

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import com.gezimos.katapult.service.DirectBadgeHelper
import com.gezimos.katapult.service.NotificationListener

/**
 * Opens a notification tapped in the lockscreen widget the way SystemUI does: dismiss the
 * keyguard first (the PIN prompt, if one is set), then send the notification's content
 * intent. Sending it straight from the service would start the app behind the keyguard.
 * Falls back to the app's launch intent when the notification is gone or has no intent.
 */
class NotificationOpenActivity : Activity() {
    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_KEY = "key"

        fun intent(
            context: Context,
            packageName: String,
            key: String?,
        ): Intent =
            Intent(context, NotificationOpenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_KEY, key)
    }

    // Asked once the window has focus: requested from onCreate, before the activity covers
    // the keyguard, SystemUI cancels the request right away. For the same reason the theme
    // is opaque — a translucent activity doesn't cover the keyguard either.
    private var dismissRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || dismissRequested) return
        dismissRequested = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            open()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()

                override fun onDismissCancelled() = close()

                override fun onDismissError() = close()
            },
        )
    }

    private fun open() {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return close()
        // Re-resolved here rather than passed in: the notification may have changed while
        // the PIN was being entered.
        val sbn = intent.getStringExtra(EXTRA_KEY)?.let { NotificationListener.find(it) }
        val contentIntent = sbn?.notification?.contentIntent
        if (sbn != null && contentIntent != null && send(contentIntent)) {
            // The app doesn't cancel it, SystemUI does — on our behalf now.
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) {
                NotificationListener.cancel(sbn.key)
            }
        } else {
            // Mudita Dial posts no missed-call notification; open its call log rather than
            // whichever tab was last used.
            val fallback = if (packageName == DirectBadgeHelper.MUDITA_DIAL) {
                Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE).setPackage(packageName)
            } else {
                packageManager.getLaunchIntentForPackage(packageName)
            }
            fallback?.let {
                try {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
        close()
    }

    private fun send(pendingIntent: PendingIntent): Boolean {
        // From Android 14 the sender has to opt in to lending its background-start privilege.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        return try {
            pendingIntent.send(this, 0, null, null, null, null, options)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun close() {
        finish()
        overridePendingTransition(0, 0)
    }
}
