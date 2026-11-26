// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*
import com.pixelvisionlab.pvautoagent.core.knowledge.agent.device.*
import com.pixelvisionlab.pvautoagent.core.device.agent.device.*

import com.pixelvisionlab.pvautoagent.agent.device.*

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

/**
 * Probe actual landing record: used on a failed liveness check to distinguish
 * "the probe was redirected by the system to the main screen (display=0)" from "the probe is on the virtual screen but renders a black frame".
 */
object VirtualSpaceProbeLanding {
    @Volatile var displayId: Int? = null
    @Volatile var landedAtMs: Long = 0L
    @Volatile private var active: java.lang.ref.WeakReference<Activity>? = null

    fun register(activity: Activity) {
        active = java.lang.ref.WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (active?.get() === activity) active = null
    }

    /**
     * Called actively by readiness after the readiness self-check completes, to destroy the probe Activity. The probe only exists to verify that the virtual screen can produce frames and accept injection,
     * and it must be closed when done: otherwise its white screen stays on the virtual screen and gets captured as a pure white frame of PVAutoAgent itself.
     * After destruction the target App lands on a clean virtual screen and is itself from the very first frame.
     */
    fun finishActive() {
        val activity = active?.get() ?: return
        active = null
        activity.runOnUiThread {
            if (!activity.isFinishing) activity.finish()
        }
    }

    fun describe(expectedDisplayId: Int): String {
        val landed = displayId ?: return "probe activity never reported a display (not launched or crashed)"
        val ageSec = (System.currentTimeMillis() - landedAtMs) / 1000
        return if (landed == expectedDisplayId) {
            "probe landed on expected display=$landed (${ageSec}s ago)"
        } else {
            "probe REDIRECTED to display=$landed, expected=$expectedDisplayId (${ageSec}s ago)"
        }
    }
}

class VirtualSpaceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VirtualSpaceProbeLanding.displayId =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.displayId
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.displayId
            }
        VirtualSpaceProbeLanding.landedAtMs = System.currentTimeMillis()
        VirtualSpaceProbeLanding.register(this)
        setContentView(View(this).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = false
        })
    }

    override fun onDestroy() {
        VirtualSpaceProbeLanding.clear(this)
        super.onDestroy()
    }
}
