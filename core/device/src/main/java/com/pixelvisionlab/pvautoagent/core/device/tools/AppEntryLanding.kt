// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.tools

import com.pixelvisionlab.pvautoagent.core.agent.tools.*

import com.pixelvisionlab.pvautoagent.core.device.tools.*

import android.content.Context
import com.pixelvisionlab.pvautoagent.core.agent.device.AppLaunchMode

/** Resolves a launcher component for landing an app on the selected display. */
object AppEntryLanding {
    /** A retained target app needs no launcher handoff before another route. */
    fun required(isVirtual: Boolean, launchMode: AppLaunchMode,
                 targetPackage: String, foregroundPackage: String?): Boolean =
        isVirtual && (launchMode != AppLaunchMode.RESUME || foregroundPackage != targetPackage)

    /** Resolves the app's launcher component; returns null when it cannot be resolved, and the caller then falls back to the plain deep link. */
    fun launcherComponent(context: Context, packageName: String): String? =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName)?.component }
            .getOrNull()
            ?.flattenToShortString()
}
