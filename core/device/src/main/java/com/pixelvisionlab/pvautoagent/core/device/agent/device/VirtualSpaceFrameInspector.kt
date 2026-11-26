// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*

import com.pixelvisionlab.pvautoagent.core.device.agent.device.*

interface VirtualSpaceFrameInspector {
    data class BlackFrameCheck(
        val isBlack: Boolean,
        val mean: Double,
        val variance: Double
    )

    fun renderWaitTimeoutMs(): Long
    fun renderWaitPollMs(): Long
    fun sidecarCallTimeoutMs(): Long
    fun sidecarCaptureTimeoutMs(): Long
    fun emptyFrameFailure(): String
    fun checkBlackFrame(pngBytes: ByteArray): BlackFrameCheck

    companion object {
        const val EMPTY_FRAME_MARKER = "[VIRTUAL_SPACE_FRAME_EMPTY]"
    }
}
