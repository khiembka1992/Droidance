// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.daemon

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.pixelvisionlab.pvautoagent.core.device.BackgroundRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.pixelvisionlab.pvautoagent.core.device.air.AirModePairingManager
import com.pixelvisionlab.pvautoagent.automation.AutomationManager
import com.pixelvisionlab.pvautoagent.debug.DebugLogManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Persistent keep-alive JobService.
 * The system wakes it up periodically to check whether the always-on foreground service is still running.
 */
class KeepAliveJobService : JobService() {

    companion object {
        private const val TAG = "KeepAliveJob"
        private const val JOB_ID = 10086
        private const val INTERVAL_MS = 15 * 60 * 1000L

        fun schedule(context: Context) {
            if (BackgroundRunState.isPaused(context)) return
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (scheduler.getPendingJob(JOB_ID) != null) return

            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, KeepAliveJobService::class.java)
            )
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build()

            val result = scheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "KeepAlive job scheduled")
            } else {
                Log.e(TAG, "KeepAlive job schedule failed")
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        if (BackgroundRunState.isPaused(this)) return false
        Log.i(TAG, "KeepAlive job triggered, service running: ${TaskExecutionService.instance != null}")
        runCatching { TaskExecutionService.startStandby(applicationContext) }
            .onFailure { Log.w(TAG, "Restart standby service failed: ${it.message}") }
        work = scope.launch {
            try {
                if (BackgroundRunState.isPaused(applicationContext)) return@launch
                val ep = EntryPointAccessors.fromApplication(applicationContext, KeepAliveEntryPoint::class.java)
                ep.airModePairingManager().keepAlive()
                ep.appUpdateManager().checkIfDue(appOpen = false)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Restore device connection failed: ${error.message}")
            } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        work?.cancel()
        return !BackgroundRunState.isPaused(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KeepAliveEntryPoint {
    fun airModePairingManager(): AirModePairingManager
    fun debugLogManager(): DebugLogManager
    fun automationManager(): AutomationManager
    fun appUpdateManager(): com.pixelvisionlab.pvautoagent.update.AppUpdateManager
    fun foregroundState(): com.pixelvisionlab.pvautoagent.agent.AppForegroundState
    fun runtimeSurfaceController(): com.pixelvisionlab.pvautoagent.ui.overlay.RuntimeSurfaceController
}
