// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.daemon

import android.app.PendingIntent
import android.app.AlarmManager
import android.app.Service
import android.content.Context
import com.droidance.core.device.presentation.LocalizedNotifications
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.droidance.R
import com.droidance.core.device.air.AirModePairingManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject

/**
 * Dịch vụ thường trú foreground
 * Hai nhiệm vụ:
 * 1. Giữ sống — thông báo thường trú trên thanh thông báo, ngăn hệ thống kill tiến trình
 * 2. Thực thi tác vụ — cập nhật thông báo hiển thị trạng thái hiện tại khi thực thi tác vụ tự động
 */
@AndroidEntryPoint
class TaskExecutionService : Service() {

    companion object {
        const val CHANNEL_ID = "droidance_task_channel"
        const val NOTIFICATION_ID = 1001

        /** Intent action: cập nhật văn bản thông báo */

        /** Intent action: chuyển sang chế độ chờ */
        const val ACTION_STANDBY = "com.droidance.STANDBY"
        const val ACTION_RESTORE = "com.droidance.RESTORE"

        /** Intent action: bắt đầu thực thi tác vụ (vào dịch vụ foreground) */
        const val ACTION_START_EXECUTION = "com.droidance.START_EXECUTION"
        /** Intent action: dừng thực thi tác vụ (thoát dịch vụ foreground) */
        const val ACTION_STOP_EXECUTION = "com.droidance.STOP_EXECUTION"
        const val EXTRA_TASK_TITLE = "task_title"

        /** Tham chiếu instance tĩnh để thành phần khác kiểm tra dịch vụ có đang chạy không */
        @Volatile
        var instance: TaskExecutionService? = null
            private set

        /** Phương thức tiện lợi: khởi động dịch vụ thường trú (chế độ chờ) */
        fun startStandby(context: Context, restoreSchedules: Boolean = false) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) return
            val appContext = context.applicationContext
            KeepAliveJobService.schedule(appContext)
            val intent = Intent(appContext, TaskExecutionService::class.java).apply {
                action = if (restoreSchedules) ACTION_RESTORE else ACTION_STANDBY
            }
            try {
                appContext.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w("TaskExecutionService", "Khởi động dịch vụ thường trú thất bại: ${e.message}")
            }
        }

        /** Phương thức tiện lợi: bắt đầu thực thi tác vụ foreground (dịch vụ foreground theo nhu cầu) */
        fun startExecution(context: Context, taskTitle: String? = null) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.droidance.runtime.RuntimeNotifications.post(context, NOTIFICATION_ID, R.string.app_name, taskTitle.orEmpty(), true)
                return
            }
            val appContext = context.applicationContext
            val intent = Intent(appContext, TaskExecutionService::class.java).apply {
                action = ACTION_START_EXECUTION
                if (!taskTitle.isNullOrBlank()) {
                    putExtra(EXTRA_TASK_TITLE, taskTitle)
                }
            }
            try {
                appContext.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w("TaskExecutionService", "Khởi động dịch vụ foreground thực thi tác vụ thất bại: ${e.message}")
            }
        }

        /** Phương thức tiện lợi: dừng thực thi tác vụ foreground và xóa thông báo thường trú */
        fun stopExecution(context: Context) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.droidance.runtime.RuntimeNotifications.cancel(context, NOTIFICATION_ID)
                return
            }
            val appContext = context.applicationContext
            val intent = Intent(appContext, TaskExecutionService::class.java).apply {
                action = ACTION_STOP_EXECUTION
            }
            try {
                appContext.startService(intent)
            } catch (e: Exception) {
                Log.w("TaskExecutionService", "Dừng dịch vụ foreground thực thi tác vụ thất bại: ${e.message}")
            }
        }

        /** ID thông báo hoàn thành tác vụ (tách khỏi thông báo thường trú, người dùng có thể vuốt bỏ) */
        const val TASK_DONE_NOTIFICATION_ID = 1002
        // Use a result-alert channel so an earlier ordinary-priority channel cannot
        // keep completion notices from appearing as heads-up alerts.
        const val TASK_DONE_CHANNEL_ID = "droidance_task_result_alerts"
        const val MANUAL_TAKEOVER_NOTIFICATION_ID = 1003
        const val MANUAL_TAKEOVER_CHANNEL_ID = "droidance_manual_takeover"
        const val TASK_STARTED_NOTIFICATION_ID = 1004

        fun refreshLanguage(context: Context) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) return
            val localized = localizedContext(context)
            val manager = NotificationManagerCompat.from(context)
            listOf(
                Triple(CHANNEL_ID, R.string.task_service_channel_name, R.string.task_service_channel_description),
                Triple(TASK_DONE_CHANNEL_ID, R.string.task_done_channel_name, R.string.task_done_channel_description),
                Triple(MANUAL_TAKEOVER_CHANNEL_ID, R.string.manual_takeover_channel_name, R.string.manual_takeover_channel_description)
            ).forEach { (id, name, description) ->
                manager.getNotificationChannel(id)?.let { existing ->
                    existing.name = localized.getString(name)
                    existing.description = localized.getString(description)
                    manager.createNotificationChannel(existing)
                }
            }
            LocalizedNotifications.refresh(context, setOf(NOTIFICATION_ID, TASK_DONE_NOTIFICATION_ID,
                MANUAL_TAKEOVER_NOTIFICATION_ID, TASK_STARTED_NOTIFICATION_ID))
        }

        private fun localizedContext(context: Context): Context =
            ContextCompat.getContextForLanguage(context)

        /**
         * Trước đây kết quả gửi thông báo bị nuốt im lặng (SecurityException bị catch bỏ qua trực tiếp), trên thiết bị thật không thể biết thông báo có
         * thực sự được gửi đi hay không. Ở đây ghi kết quả (gồm "công tắc thông báo tổng có bị tắt không" — nguyên nhân thất bại im lặng phổ biến hơn cả exception)
         * vào debugLogManager để lần xuất debug sau thấy được ngay.
         */
        private fun logNotifyResult(context: Context, kind: String, result: Result<Unit>) {
            runCatching {
                val debugLogManager = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    KeepAliveEntryPoint::class.java
                ).debugLogManager()
                val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                val detail = result.exceptionOrNull()?.let { "failed: ${it.message}" } ?: "sent"
                debugLogManager.appendEvent("🔔 notify($kind): $detail, notifications_enabled=$enabled")
            }
        }

        /** Gửi thông báo bắt đầu tác vụ theo lịch (tình huống b) */
        fun notifyAutomationStarted(context: Context, taskTitle: String): Boolean {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.droidance.runtime.RuntimeNotifications.post(context, TASK_STARTED_NOTIFICATION_ID, R.string.automation_started_notification_title, taskTitle)
            if (suppressForegroundNotification(context, "task_started")) return true
            val l10n = localizedContext(context)
            val channel = NotificationChannelCompat.Builder(
                TASK_DONE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.task_done_channel_name))
                .setDescription(l10n.getString(R.string.task_done_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context, 4, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val text = l10n.getString(R.string.automation_started_notification_text, taskTitle)
            val notification = NotificationCompat.Builder(l10n, TASK_DONE_CHANNEL_ID)
                .apply { LocalizedNotifications.setText(this, l10n, R.string.automation_started_notification_title, R.string.automation_started_notification_text, taskTitle) }
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setAutoCancel(true)
                // Progress shares the result channel without alerting before completion.
                .setSilent(true)
                .setContentIntent(pendingIntent)
                .build()

            val result = try {
                nm.notify(TASK_STARTED_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
            logNotifyResult(context, "task_started", result)
            return result.isSuccess && nm.areNotificationsEnabled()
        }

        /** Gửi thông báo hoàn thành tác vụ (tình huống e) */
        fun notifyTaskCompleted(context: Context, taskTitle: String? = null, summary: String): Boolean {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.droidance.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_completed_notification_title, summary)
            if (suppressForegroundNotification(context, "task_complete")) return true
            val l10n = localizedContext(context)
            val channel = NotificationChannelCompat.Builder(
                TASK_DONE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.task_done_channel_name))
                .setDescription(l10n.getString(R.string.task_done_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context, 1, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val text = summary.trim()
            val notification = NotificationCompat.Builder(l10n, TASK_DONE_CHANNEL_ID)
                .apply { LocalizedNotifications.setText(this, l10n, R.string.task_completed_notification_title) }
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val result = try {
                nm.notify(TASK_DONE_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
            logNotifyResult(context, "task_complete", result)
            return result.isSuccess && nm.areNotificationsEnabled()
        }

        /** Gửi thông báo tác vụ bị ngắt/thất bại (tình huống c) */
        fun notifyTaskInterrupted(context: Context, taskTitle: String? = null, reason: String): Boolean {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.droidance.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_interrupted_notification_title, reason)
            if (suppressForegroundNotification(context, "task_interrupted")) return true
            val l10n = localizedContext(context)
            val channel = NotificationChannelCompat.Builder(
                TASK_DONE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.task_done_channel_name))
                .setDescription(l10n.getString(R.string.task_done_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context, 1, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val text = reason.trim().ifBlank { l10n.getString(R.string.task_interrupted_default_text) }
            val notification = NotificationCompat.Builder(l10n, TASK_DONE_CHANNEL_ID)
                .apply { LocalizedNotifications.setText(this, l10n, R.string.task_interrupted_notification_title, if (reason.isBlank()) R.string.task_interrupted_default_text else 0) }
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val result = try {
                nm.notify(TASK_DONE_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
            logNotifyResult(context, "task_interrupted", result)
            return result.isSuccess && nm.areNotificationsEnabled()
        }

        private fun isAppForeground(context: Context): Boolean = runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                KeepAliveEntryPoint::class.java
            )
            // Cross-display input can pause the viewer while it remains visible.
            entryPoint.foregroundState().isInForeground ||
                entryPoint.runtimeSurfaceController().isViewerExpanded
        }.getOrDefault(false)

        /** In-app presentation counts as delivery, so scheduled results are not retried later. */
        private fun suppressForegroundNotification(context: Context, kind: String): Boolean {
            if (!isAppForeground(context)) return false
            logNotifyResult(context, "${kind}_suppressed_foreground", Result.success(Unit))
            return true
        }

        /**
         * Gửi thông báo kết thúc tác vụ (thông báo độc lập, không thường trú).
         *
         * Khi tác vụ hoàn thành: nếu ứng dụng đang ở foreground giao diện (tức đang trong cửa sổ trò chuyện) và đã hiển thị trong giao diện ứng dụng thì không cần bật thông báo nữa;
         * chỉ khi ứng dụng không ở foreground (tức đang ở ngoài ứng dụng) mới cần bật thông báo.
         *
         * Nội dung chính là đoạn văn bản cuối cùng mà mô hình gửi cho người dùng: không có tiêu đề thành công/thất bại, không thêm tiền tố, mở rộng là toàn văn.
         */
        fun notifyTaskDone(context: Context, text: String): Boolean {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.droidance.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_completed_notification_title, text)
            if (suppressForegroundNotification(context, "task_complete")) return true
            val l10n = localizedContext(context)
            // Tạo kênh thông báo (ưu tiên cao, có âm thanh)
            val channel = NotificationChannelCompat.Builder(
                TASK_DONE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.task_done_channel_name))
                .setDescription(l10n.getString(R.string.task_done_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            // Nhấn để quay lại màn hình chính
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context, 1, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val notification = NotificationCompat.Builder(l10n, TASK_DONE_CHANNEL_ID)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val result = try {
                nm.notify(TASK_DONE_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
            logNotifyResult(context, "task_complete", result)
            return result.isSuccess && nm.areNotificationsEnabled()
        }
        fun notifyManualTakeoverRequired(context: Context, detail: String? = null) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.droidance.runtime.RuntimeNotifications.post(context, MANUAL_TAKEOVER_NOTIFICATION_ID,
                    R.string.manual_takeover_channel_name, detail.orEmpty())
                return
            }
            if (suppressForegroundNotification(context, "manual_takeover")) return
            val l10n = localizedContext(context)
            val channel = NotificationChannelCompat.Builder(
                MANUAL_TAKEOVER_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.manual_takeover_channel_name))
                .setDescription(l10n.getString(R.string.manual_takeover_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = if (launchIntent != null) {
                PendingIntent.getActivity(
                    context,
                    2,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            val manualText = detail?.trim()?.takeIf { it.isNotBlank() }
                ?: l10n.getString(R.string.manual_takeover_notification_text)
            val notification = NotificationCompat.Builder(l10n, MANUAL_TAKEOVER_CHANNEL_ID)
                .apply { LocalizedNotifications.setText(this, l10n, R.string.manual_takeover_notification_title, if (detail.isNullOrBlank()) R.string.manual_takeover_notification_text else 0) }
                .setContentText(manualText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(manualText))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()

            val result = try {
                nm.notify(MANUAL_TAKEOVER_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
            logNotifyResult(context, "manual_takeover", result)
        }

        /** Background tasks cannot force a runtime-permission dialog; this opens the normal host. */
        fun notifyRuntimePermissionRequired(context: Context, detail: String) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) return
            if (suppressForegroundNotification(context, "runtime_permission")) return
            val l10n = localizedContext(context)
            val channel = NotificationChannelCompat.Builder(
                MANUAL_TAKEOVER_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.manual_takeover_channel_name))
                .setDescription(l10n.getString(R.string.manual_takeover_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    3,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val text = detail.trim().ifBlank {
                l10n.getString(R.string.runtime_permission_notification_text)
            }
            val notification = NotificationCompat.Builder(l10n, MANUAL_TAKEOVER_CHANNEL_ID)
                .apply { LocalizedNotifications.setText(this, l10n, R.string.runtime_permission_notification_title, if (detail.isBlank()) R.string.runtime_permission_notification_text else 0) }
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()
            val result = try {
                nm.notify(MANUAL_TAKEOVER_NOTIFICATION_ID, notification)
                Result.success(Unit)
            } catch (error: SecurityException) {
                Result.failure(error)
            }
            logNotifyResult(context, "runtime_permission", result)
        }

        fun cancelManualTakeover(context: Context) {
            if (com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.droidance.runtime.RuntimeNotifications.cancel(context, MANUAL_TAKEOVER_NOTIFICATION_ID)
                return
            }
            try {
                NotificationManagerCompat.from(context).cancel(MANUAL_TAKEOVER_NOTIFICATION_ID)
            } catch (_: SecurityException) {
                // Quyền thông báo bị từ chối
            }
        }
    }

    @Inject lateinit var airModePairingManager: AirModePairingManager

    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restoreRequests = Channel<Unit>(Channel.CONFLATED)

    private val keepAliveWakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Droidance::KeepAliveWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    private val keepAliveWifiLock by lazy {
        @Suppress("DEPRECATION")
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "Droidance::KeepAliveWifiLock"
        ).apply { setReferenceCounted(false) }
    }

    @Volatile private var isStandby = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        KeepAliveJobService.schedule(this)
        restoreScope.launch { airModePairingManager.ensureConnectedIfPaired() }
        restoreScope.launch {
            for (request in restoreRequests) {
                try {
                    airModePairingManager.ensureConnectedIfPaired()
                    EntryPointAccessors.fromApplication(applicationContext, KeepAliveEntryPoint::class.java)
                        .automationManager().restoreSchedules()
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { Log.w("TaskExecutionService", "Background restore failed", error) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val l10n = ContextCompat.getContextForLanguage(this)
        when (intent?.action) {
            ACTION_STOP_EXECUTION -> {
                val shouldKeepStandby = airModePairingManager.isPreviouslyPaired
                if (shouldKeepStandby) {
                    isStandby = true
                    val notification = buildNotification(l10n, R.string.task_service_standby_status)
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                    } catch (e: Exception) {
                        Log.w("TaskExecutionService", "Vào foreground chế độ chờ thất bại: ${e.message}")
                    }
                    acquireKeepAliveWakeLock()
                    acquireKeepAliveWifiLock()
                    return START_STICKY
                } else {
                    releaseKeepAliveWakeLock()
                    releaseKeepAliveWifiLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }
            ACTION_START_EXECUTION -> {
                isStandby = false
                val title = intent.getStringExtra(EXTRA_TASK_TITLE)
                val notification = if (!title.isNullOrBlank()) {
                    buildNotification(l10n, R.string.task_service_running_named, title)
                } else {
                    buildNotification(l10n, R.string.task_service_running_status)
                }
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.w("TaskExecutionService", "Vào dịch vụ foreground thực thi thất bại: ${e.message}")
                }
                acquireKeepAliveWakeLock()
                acquireKeepAliveWifiLock()
                return START_NOT_STICKY
            }
            ACTION_STANDBY, ACTION_RESTORE -> {
                isStandby = true
                val notification = buildNotification(l10n, R.string.task_service_standby_status)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.w("TaskExecutionService", "Vào foreground chế độ chờ thất bại: ${e.message}")
                }
                acquireKeepAliveWakeLock()
                acquireKeepAliveWifiLock()
                if (intent.action == ACTION_RESTORE) restoreRequests.trySend(Unit)
                return START_STICKY
            }
            else -> {
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        restoreScope.cancel()
        restoreRequests.close()
        instance = null
        if (isStandby) {
            scheduleRestart(0)
        }
        releaseKeepAliveWakeLock()
        releaseKeepAliveWifiLock()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isStandby) {
            scheduleRestart(1)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(l10n: Context, @StringRes statusResource: Int, vararg arguments: String): android.app.Notification {
        // Nhấn thông báo để quay lại màn hình chính
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(l10n, CHANNEL_ID)
            .apply { LocalizedNotifications.setText(this, l10n, R.string.app_name, statusResource, *arguments) }
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireKeepAliveWakeLock() {
        try {
            if (!keepAliveWakeLock.isHeld) {
                keepAliveWakeLock.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (error: Exception) {
            Log.w("TaskExecutionService", "Keep-alive wake lock acquisition failed: ${error.javaClass.simpleName}")
        }
    }

    private fun releaseKeepAliveWakeLock() {
        try {
            if (keepAliveWakeLock.isHeld) {
                keepAliveWakeLock.release()
            }
        } catch (error: Exception) {
            Log.w("TaskExecutionService", "Keep-alive wake lock release failed: ${error.javaClass.simpleName}")
        }
    }

    private fun acquireKeepAliveWifiLock() {
        try {
            if (!keepAliveWifiLock.isHeld) {
                keepAliveWifiLock.acquire()
            }
        } catch (error: Exception) {
            Log.w("TaskExecutionService", "Keep-alive Wi-Fi lock acquisition failed: ${error.javaClass.simpleName}")
        }
    }

    private fun releaseKeepAliveWifiLock() {
        try {
            if (keepAliveWifiLock.isHeld) {
                keepAliveWifiLock.release()
            }
        } catch (error: Exception) {
            Log.w("TaskExecutionService", "Keep-alive Wi-Fi lock release failed: ${error.javaClass.simpleName}")
        }
    }

    private fun scheduleRestart(requestCode: Int) {
        val restartIntent = Intent(applicationContext, TaskExecutionService::class.java).apply {
            action = ACTION_STANDBY
        }
        val pendingRestart = PendingIntent.getService(
            applicationContext,
            requestCode,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3_000L,
                pendingRestart
            )
        } catch (error: Exception) {
            Log.w("TaskExecutionService", "Standby restart alarm scheduling failed: ${error.javaClass.simpleName}")
        }
    }

    private fun createNotificationChannel() {
        val l10n = ContextCompat.getContextForLanguage(this)
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(l10n.getString(R.string.task_service_channel_name))
            .setDescription(l10n.getString(R.string.task_service_channel_description))
            .build()

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
}
