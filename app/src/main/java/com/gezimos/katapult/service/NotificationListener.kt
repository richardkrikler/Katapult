package com.gezimos.katapult.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gezimos.katapult.lockscreen.LockscreenPrivacy
import com.gezimos.katapult.util.AudioWidgetHelper
import com.gezimos.katapult.util.DeviceHelper
import java.util.concurrent.ConcurrentHashMap

class NotificationListener : NotificationListenerService() {

    companion object {

        private val keysByPkg = ConcurrentHashMap<String, MutableSet<String>>()

        private val clearedKeys = ConcurrentHashMap<String, MutableSet<String>>()

        // Latest notification post time per package, for the lockscreen widget sort order.
        private val lastPostByPkg = ConcurrentHashMap<String, Long>()

        var onCountsChanged: (() -> Unit)? = null
        // Extra slots for the experimental lockscreen widget and screensaver (see .lockscreen package).
        var onCountsChangedExtra: (() -> Unit)? = null
        var onCountsChangedDream: (() -> Unit)? = null
        private val skipPackages = if (DeviceHelper.isMuditaKompakt()) DirectBadgeHelper.DIRECT_PACKAGES else emptySet()

        // System packages whose notifications are never user-relevant (USB mode,
        // "app running in background", update prompts, etc.).
        private val systemNoisePackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.gms",
            "com.mudita.service", // "KompaktOsApi" background service
            "com.mediatek.duraspeed", // MediaTek background-app manager
        )

        private fun notifyChanged() {
            onCountsChanged?.invoke()
            onCountsChangedExtra?.invoke()
            onCountsChangedDream?.invoke()
        }

        @Volatile
        private var instance: NotificationListener? = null

        private fun newKeySet(): MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()

        fun getCount(packageName: String): Int = keysByPkg[packageName]?.size ?: 0

        fun getAllCounts(): Map<String, Int> = keysByPkg.mapValues { it.value.size }

        fun getLastPostTimes(): Map<String, Long> = lastPostByPkg.toMap()

        /**
         * Live notifications for [packageName] the lockscreen widget can open. Queried on
         * tap only, so no Notification objects are kept around between taps.
         */
        fun activeFor(packageName: String): List<StatusBarNotification> {
            val self = instance ?: return emptyList()
            return try {
                self.activeNotifications.filter { it.packageName == packageName && self.isListable(it) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun find(key: String): StatusBarNotification? = try {
            instance?.getActiveNotifications(arrayOf(key))?.firstOrNull()
        } catch (_: Exception) {
            null
        }

        /** The user's per-channel lock screen visibility for [key], or LockscreenPrivacy.NO_OVERRIDE. */
        fun lockscreenVisibilityOverride(key: String): Int {
            val self = instance ?: return LockscreenPrivacy.NO_OVERRIDE
            return try {
                val ranking = NotificationListenerService.Ranking()
                if (self.currentRanking.getRanking(key, ranking)) {
                    ranking.channel?.lockscreenVisibility ?: LockscreenPrivacy.NO_OVERRIDE
                } else {
                    LockscreenPrivacy.NO_OVERRIDE
                }
            } catch (_: Exception) {
                LockscreenPrivacy.NO_OVERRIDE
            }
        }

        fun cancel(key: String) {
            try { instance?.cancelNotification(key) } catch (_: Exception) {}
        }

        fun cancelFor(packageName: String) {
            val self = instance ?: return
            if (packageName in skipPackages) return

            val keys: List<String> = try {
                self.activeNotifications
                    .filter { it.packageName == packageName }
                    .map { it.key }
            } catch (_: Exception) {
                emptyList()
            }

            if (keys.isNotEmpty()) {
                clearedKeys.getOrPut(packageName) { newKeySet() }.addAll(keys)
            }

            keysByPkg.remove(packageName)
            lastPostByPkg.remove(packageName)
            notifyChanged()

            if (keys.isEmpty()) return


            try {
                self.cancelNotifications(keys.toTypedArray())
            } catch (_: Exception) {
                for (key in keys) {
                    try { self.cancelNotification(key) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun isGroupSummary(sbn: StatusBarNotification): Boolean =
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun shouldCount(sbn: StatusBarNotification): Boolean =
        sbn.packageName !in skipPackages && isListable(sbn)

    // Unlike shouldCount, keeps the Mudita Dial/Messages notifications: their counts come
    // from DirectBadgeHelper, but the notifications themselves can still be opened.
    private fun isListable(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in systemNoisePackages) return false
        // Persistent notifications (foreground services, media, ongoing calls)
        // are status, not something the user needs to act on.
        if (!sbn.isClearable) return false
        if (isGroupSummary(sbn)) return false
        if (clearedKeys[sbn.packageName]?.contains(sbn.key) == true) return false
        return true
    }

    override fun onListenerConnected() {
        instance = this
        keysByPkg.clear()
        lastPostByPkg.clear()
        val active = try { activeNotifications } catch (_: Exception) { null }
        for (sbn in active.orEmpty()) {
            if (!shouldCount(sbn)) continue
            keysByPkg.getOrPut(sbn.packageName) { newKeySet() }.add(sbn.key)
            lastPostByPkg.merge(sbn.packageName, sbn.postTime) { a, b -> maxOf(a, b) }
        }
        notifyChanged()

        val componentName = ComponentName(this, NotificationListener::class.java)
        AudioWidgetHelper.getInstance(this).initialize(componentName)
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldCount(sbn)) {
            // A counted key can come back uncountable, e.g. AOSP Messaging re-posts its
            // single-conversation notification as the group summary once a second
            // conversation arrives.
            uncount(sbn)
            return
        }
        val set = keysByPkg.getOrPut(sbn.packageName) { newKeySet() }
        lastPostByPkg.merge(sbn.packageName, sbn.postTime) { a, b -> maxOf(a, b) }
        if (set.add(sbn.key)) {
            notifyChanged()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        clearedKeys[sbn.packageName]?.let { set ->
            set.remove(sbn.key)
            if (set.isEmpty()) clearedKeys.remove(sbn.packageName)
        }
        uncount(sbn)
    }

    private fun uncount(sbn: StatusBarNotification) {
        val set = keysByPkg[sbn.packageName] ?: return
        if (set.remove(sbn.key)) {
            if (set.isEmpty()) {
                keysByPkg.remove(sbn.packageName)
                lastPostByPkg.remove(sbn.packageName)
            }
            notifyChanged()
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        AudioWidgetHelper.getInstance(this).cleanup()
    }
}
