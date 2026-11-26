// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

import kotlinx.serialization.Serializable

@Serializable
enum class ToolEventStatus {
    SUCCEEDED,
    FAILED,
    UNVERIFIED
}

@Serializable
enum class ExecutionTerminalStatus {
    DONE,
    NEED_MORE_INFO,
    FAILED,
    ERROR,
    CANCELED
}
