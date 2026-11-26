// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.daemon

import android.app.PendingIntent
import android.app.AlarmManager
import android.app.Service
import android.content.Context
import com.pixelvisionlab.pvautoagent.core.device.presentation.LocalizedNotifications
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
import com.pixelvisionlab.pvautoagent.R
import com.pixelvisionlab.pvautoagent.core.device.BackgroundRunState
import com.pixelvisionlab.pvautoagent.core.device.air.AirModePairingManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject

/**
 * Resident foreground service
 * Dual responsibility:
 * 1. Keep-alive — stays in the notification shade so the system does not kill the process
 * 2. Task execution — updates the notification to show the current state while running automation tasks
 */
@AndroidEntryPoint
class TaskExecutionService : Service() {

    companion object {
        const val CHANNEL_ID = "pvautoagent_task_channel"
        const val NOTIFICATION_ID = 1001

        /** Intent action: update the notification text */

        /** Intent action: switch to standby mode */
        const val ACTION_STANDBY = "com.pixelvisionlab.pvautoagent.STANDBY"
        const val ACTION_RESTORE = "com.pixelvisionlab.pvautoagent.RESTORE"

        /** Intent action: start executing a task (enter the foreground service) */
        const val ACTION_START_EXECUTION = "com.pixelvisionlab.pvautoagent.START_EXECUTION"
        /** Intent action: stop executing the task (leave the foreground service) */
        const val ACTION_STOP_EXECUTION = "com.pixelvisionlab.pvautoagent.STOP_EXECUTION"
        const val EXTRA_TASK_TITLE = "task_title"

        /** Static instance reference so other components can check whether the service is running */
        @Volatile
        var instance: TaskExecutionService? = null
            private set

        /** Convenience method: start the resident service (standby mode) */
        fun startStandby(context: Context, restoreSchedules: Boolean = false) {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) return
            if (BackgroundRunState.isPaused(context)) return
            val appContext = context.applicationContext
            KeepAliveJobService.schedule(appContext)
            val intent = Intent(appContext, TaskExecutionService::class.java).apply {
                action = if (restoreSchedules) ACTION_RESTORE else ACTION_STANDBY
            }
            try {
                appContext.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w("TaskExecutionService", "Failed to start the resident service: ${e.message}")
            }
        }

        /** Convenience method: start foreground task execution (on-demand foreground service) */
        fun startExecution(context: Context, taskTitle: String? = null) {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, NOTIFICATION_ID, R.string.app_name, taskTitle.orEmpty(), true)
                return
            }
            if (BackgroundRunState.isPaused(context)) return
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
                Log.w("TaskExecutionService", "Failed to start the task execution foreground service: ${e.message}")
            }
        }

        /** Convenience method: stop foreground task execution and clear the resident notification */
        fun stopExecution(context: Context) {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.cancel(context, NOTIFICATION_ID)
                return
            }
            if (BackgroundRunState.isPaused(context)) return
            val appContext = context.applicationContext
            val intent = Intent(appContext, TaskExecutionService::class.java).apply {
                action = ACTION_STOP_EXECUTION
            }
            try {
                appContext.startService(intent)
            } catch (e: Exception) {
                Log.w("TaskExecutionService", "Failed to stop the task execution foreground service: ${e.message}")
            }
        }

        /** Task completion notification ID (independent of the resident notification, can be swiped away by the user) */
        const val TASK_DONE_NOTIFICATION_ID = 1002
        // Use a result-alert channel so an earlier ordinary-priority channel cannot
        // keep completion notices from appearing as heads-up alerts.
        const val TASK_DONE_CHANNEL_ID = "pvautoagent_task_result_alerts"
        const val MANUAL_TAKEOVER_NOTIFICATION_ID = 1003
        const val MANUAL_TAKEOVER_CHANNEL_ID = "pvautoagent_manual_takeover"
        const val TASK_STARTED_NOTIFICATION_ID = 1004

        internal fun stopStandby(context: Context) {
            val intent = standbyIntent(context)
            val alarms = context.getSystemService(AlarmManager::class.java)
            for (requestCode in 0..1) {
                PendingIntent.getService(context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )?.let { alarms.cancel(it); it.cancel() }
            }
            context.stopService(intent)
        }

        private fun standbyIntent(context: Context) =
            Intent(context, TaskExecutionService::class.java).setAction(ACTION_STANDBY)

        fun refreshLanguage(context: Context) {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) return
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
         * The notification send result used to be swallowed silently (SecurityException was caught and ignored outright), so on real devices there was
         * no way to see whether a notification actually went out. This records the result (including "the master notification toggle is off", a silent failure cause
         * more common than an exception) into debugLogManager, so the next debug export shows it directly.
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

        /** Send the scheduled task start notification (scenario b) */
        fun notifyAutomationStarted(context: Context, taskTitle: String): Boolean {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, TASK_STARTED_NOTIFICATION_ID, R.string.automation_started_notification_title, taskTitle)
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

        /** Send the task completed notification (scenario e) */
        fun notifyTaskCompleted(context: Context, taskTitle: String? = null, summary: String): Boolean {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_completed_notification_title, summary)
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

        /** Send the task interrupted/failed notification (scenario c) */
        fun notifyTaskInterrupted(context: Context, taskTitle: String? = null, reason: String): Boolean {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_interrupted_notification_title, reason)
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
         * Send the task finished notification (a standalone notification, not the resident one).
         *
         * When the task completes: if the app is in the foreground (that is, in the conversation window) and already displayed in the app UI, there is no need to post a pop-up notification;
         * only if the app is not in the foreground (that is, outside the app) does a pop-up notification become necessary.
         *
         * The body is the final message the model gives the user itself: no success/failure title, no prefix, expanding shows the full text.
         */
        fun notifyTaskDone(context: Context, text: String): Boolean {
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner)
                return com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, TASK_DONE_NOTIFICATION_ID, R.string.task_completed_notification_title, text)
            if (suppressForegroundNotification(context, "task_complete")) return true
            val l10n = localizedContext(context)
            // Create the notification channel (high priority, with sound)
            val channel = NotificationChannelCompat.Builder(
                TASK_DONE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
                .setName(l10n.getString(R.string.task_done_channel_name))
                .setDescription(l10n.getString(R.string.task_done_channel_description))
                .build()
            val nm = NotificationManagerCompat.from(context)
            nm.createNotificationChannel(channel)

            // Tap to jump back to the main screen
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
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.post(context, MANUAL_TAKEOVER_NOTIFICATION_ID,
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
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) return
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
            if (com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeProcessRole.isOwner) {
                com.pixelvisionlab.pvautoagent.runtime.RuntimeNotifications.cancel(context, MANUAL_TAKEOVER_NOTIFICATION_ID)
                return
            }
            try {
                NotificationManagerCompat.from(context).cancel(MANUAL_TAKEOVER_NOTIFICATION_ID)
            } catch (_: SecurityException) {
                // Notification permission denied
            }
        }
    }

    @Inject lateinit var airModePairingManager: AirModePairingManager

    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restoreRequests = Channel<Unit>(Channel.CONFLATED)

    private val keepAliveWakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PVAutoAgent::KeepAliveWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    private val keepAliveWifiLock by lazy {
        @Suppress("DEPRECATION")
        (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "PVAutoAgent::KeepAliveWifiLock"
        ).apply { setReferenceCounted(false) }
    }

    @Volatile private var isStandby = false
    private var executionTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BackgroundRunState.isPaused(this)) return
        createNotificationChannel()
        KeepAliveJobService.schedule(this)
        restoreScope.launch { airModePairingManager.ensureConnectedIfPaired() }
        restoreScope.launch {
            for (request in restoreRequests) {
                if (BackgroundRunState.isPaused(this@TaskExecutionService)) continue
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
        if (BackgroundRunState.isPaused(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START_EXECUTION -> {
                isStandby = false
                executionTitle = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
            }
            ACTION_STOP_EXECUTION -> {
                executionTitle = null
                isStandby = true
            }
            ACTION_STANDBY, ACTION_RESTORE, null -> {
                isStandby = executionTitle == null
                if (intent?.action == ACTION_RESTORE) restoreRequests.trySend(Unit)
            }
            else -> return START_NOT_STICKY
        }

        val l10n = ContextCompat.getContextForLanguage(this)
        val title = executionTitle
        val notification = when {
            title == null -> buildNotification(l10n, R.string.task_service_standby_status)
            title.isBlank() -> buildNotification(l10n, R.string.task_service_running_status)
            else -> buildNotification(l10n, R.string.task_service_running_named, title)
        }
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w("TaskExecutionService", "Failed to start foreground service", e)
        }
        acquireKeepAliveWakeLock()
        acquireKeepAliveWifiLock()
        return if (isStandby) START_STICKY else START_NOT_STICKY
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
        stopForeground(STOP_FOREGROUND_REMOVE)
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
        // Tap the notification to jump back to the main screen
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
        if (BackgroundRunState.isPaused(this)) return
        val pendingRestart = PendingIntent.getService(
            applicationContext,
            requestCode,
            standbyIntent(applicationContext),
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
