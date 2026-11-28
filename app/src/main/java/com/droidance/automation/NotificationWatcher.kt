// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.automation

import android.app.ActivityOptions
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Dịch vụ lắng nghe thông báo
 * Bắt và phát các sự kiện thông báo thô.
 */
class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"

        /** Tham chiếu instance toàn cục */
        var instance: NotificationWatcher? = null
            private set

        /** Luồng sự kiện thông báo */
        private val _notificationFlow = MutableSharedFlow<NotificationInfo>(extraBufferCapacity = 50)
        val notificationFlow: SharedFlow<NotificationInfo> = _notificationFlow
        internal fun publish(info: NotificationInfo) { _notificationFlow.tryEmit(info) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Dịch vụ lắng nghe thông báo đã kết nối")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

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

        // Phát sự kiện thông báo
        _notificationFlow.tryEmit(info)

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Xử lý khi thông báo bị xóa (tùy chọn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "Dịch vụ lắng nghe thông báo đã ngắt kết nối")
    }

    /**
     * Nhấn vào thông báo (kích hoạt PendingIntent của nó, tương đương người dùng nhấn vào thanh thông báo)
     * @param key mã định danh duy nhất của thông báo
     * @return liệu có thành công
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
     * Xóa thông báo được chỉ định
     * @param key mã định danh duy nhất của thông báo
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
     * Đọc tất cả thông báo đang hoạt động hiện tại
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
 * Lớp dữ liệu thông tin thông báo
 */
@kotlinx.serialization.Serializable
data class NotificationInfo(
    val packageName: String,   // Tên gói của App nguồn
    val appName: String,       // Tên App nguồn
    val title: String,         // Tiêu đề thông báo
    val content: String,       // Nội dung thông báo
    val timestamp: Long,       // Thời điểm thông báo
    val key: String            // Mã định danh duy nhất của thông báo
)
