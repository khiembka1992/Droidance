// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.device.tools

import com.droidance.core.agent.tools.*

import com.droidance.core.device.tools.*

import android.content.Context
import com.droidance.core.agent.device.AppLaunchMode

/** Resolves a launcher component for landing an app on the selected display. */
object AppEntryLanding {
    /** A retained target app needs no launcher handoff before another route. */
    fun required(isVirtual: Boolean, launchMode: AppLaunchMode,
                 targetPackage: String, foregroundPackage: String?): Boolean =
        isVirtual && (launchMode != AppLaunchMode.RESUME || foregroundPackage != targetPackage)

    /** Phân giải launcher component của App; nếu không phân giải được thì trả về null, bên gọi lùi về deep link thuần. */
    fun launcherComponent(context: Context, packageName: String): String? =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName)?.component }
            .getOrNull()
            ?.flattenToShortString()
}
