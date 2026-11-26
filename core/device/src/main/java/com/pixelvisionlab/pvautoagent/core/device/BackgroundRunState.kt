// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device

import android.content.Context
import java.io.File

/** The notification listener and the UI run in different processes. */
object BackgroundRunState {
    private fun pauseMarker(context: Context) = File(context.noBackupFilesDir, "background-paused")

    fun isPaused(context: Context): Boolean = pauseMarker(context).exists()

    fun pause(context: Context) {
        val marker = pauseMarker(context)
        check(marker.createNewFile() || marker.isFile) { "Cannot pause PVAutoAgent background work" }
    }

    fun resume(context: Context): Boolean {
        val marker = pauseMarker(context)
        if (!marker.exists()) return false
        check(marker.delete()) { "Cannot resume PVAutoAgent background work" }
        return true
    }
}
