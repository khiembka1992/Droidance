// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0
package com.pixelvisionlab.pvautoagent.agent.device

import android.os.Bundle
import com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimePlatformDriver
import com.pixelvisionlab.pvautoagent.core.device.agent.device.RuntimeWire
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeInputDriverClient @Inject constructor() {
    fun command(operation: String, arguments: Bundle = Bundle()): Bundle {
        return try {
            RuntimeWire.read(RuntimePlatformDriver.connect().command(operation, arguments))
        } catch (error: android.os.DeadObjectException) {
            // A restarted app may have left a dead driver during an idempotent readiness probe.
            // Never replay input or other operations whose delivery may already have occurred.
            if (operation !in setOf("probeReset", "probeState")) throw error
            RuntimeWire.read(RuntimePlatformDriver.connect().command(operation, arguments))
        }
    }
}
