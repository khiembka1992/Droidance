// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.automation

import android.app.ActivityOptions
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pixelvisionlab.pvautoagent.core.device.BackgroundRunState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Notification listener service
 * Captures and publishes raw notification events.
 */
class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"

        /** Global instance reference */
        var instance: NotificationWatcher? = null
            private set

        /** Notification event flow */
        private val _notificationFlow = MutableSharedFlow<NotificationInfo>(extraBufferCapacity = 50)
        val notificationFlow: SharedFlow<NotificationInfo> = _notificationFlow
        internal fun publish(info: NotificationInfo) { _notificationFlow.tryEmit(info) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener service connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (BackgroundRunState.isPaused(this)) return

        val notification = sbn.notification
        val extras = notification.extras
        val info = NotificationInfo(
            packageName = sbn.packageName,
            appName = extras.getString("android.title.big")
                ?: extras.getString("android.title")
                ?: sbn.packageName,
            title = extras.getCharSequence("android.title")?.toString() ?: "",
            content = extras.getCharSequence("android.text")?.toString() ?: "",
            timestamp = sbn.postTime,
            key = sbn.key
        )

        Log.d(TAG, "Notification received; package=${info.packageName}")

        // Emit the notification event
        _notificationFlow.tryEmit(info)

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Handling when a notification is cleared (optional)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "Notification listener service disconnected")
    }

    /**
     * Click the notification (triggers its PendingIntent, equivalent to the user tapping the notification shade)
     * @param key Unique identifier of the notification
     * @return Whether it succeeded
     */
    fun clickNotification(key: String, displayId: Int? = null): Boolean {
        return try {
            val sbn = activeNotifications?.find { it.key == key }
            val contentIntent = sbn?.notification?.contentIntent
            if (contentIntent != null) {
                val options = displayId?.let {
                    ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle()
                }
                contentIntent.send(this, 0, null, null, null, null, options)
                Log.i(TAG, "Notification opened")
                true
            } else {
                Log.w(TAG, "Notification unavailable")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification open failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Dismiss the specified notification
     * @param key Unique identifier of the notification
     */
    fun dismissNotification(key: String) {
        try {
            cancelNotification(key)
            Log.i(TAG, "Notification dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Notification dismissal failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Read all currently active notifications
     */
    fun getAllNotifications(): List<NotificationInfo> {
        return try {
            activeNotifications?.map { sbn ->
                val extras = sbn.notification.extras
                NotificationInfo(
                    packageName = sbn.packageName,
                    appName = extras.getCharSequence("android.title")?.toString() ?: sbn.packageName,
                    title = extras.getCharSequence("android.title")?.toString() ?: "",
                    content = extras.getCharSequence("android.text")?.toString() ?: "",
                    timestamp = sbn.postTime,
                    key = sbn.key
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Notification read failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }
}

/**
 * Notification info data class
 */
@kotlinx.serialization.Serializable
data class NotificationInfo(
    val packageName: String,   // Package name of the source app
    val appName: String,       // Name of the source app
    val title: String,         // Notification title
    val content: String,       // Notification content
    val timestamp: Long,       // Notification time
    val key: String            // Unique notification identifier
)
