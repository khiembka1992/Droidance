// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*

import com.pixelvisionlab.pvautoagent.core.device.agent.device.*

data class DisplaySnapshot(
    val observationId: String,
    val backendSessionId: String,
    val displayId: Int,
    val imagePng: ByteArray,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val foregroundComponent: String?,
    val capturedAtElapsedMs: Long,
    /** The exact captured source owns reusable derivatives; never looked up from a later cache. */
    val nativeFrame: com.pixelvisionlab.pvautoagent.core.device.tools.DeviceScreenFrame? = null
)
