// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.daemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pixelvisionlab.pvautoagent.core.device.BackgroundRunState

/**
 * Boot broadcast receiver
 * Restores scheduled tasks and the heartbeat mechanism after the device boots
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS || BackgroundRunState.isPaused(context)) return
        Log.i("BootReceiver", "Device boot detected; restoring PVAutoAgent background work")
        // HeartbeatManager uses WorkManager, so the system restores it automatically
        KeepAliveJobService.schedule(context)
        // Returning releases the broadcast queue needed by the runtime's Binder handoff.
        TaskExecutionService.startStandby(context, restoreSchedules = true)
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
