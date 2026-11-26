// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

enum class AgentState {
    IDLE,            // Idle, waiting for input
    THINKING,        // Calling the LLM for reasoning
    EXECUTING_TOOL,  // Executing a tool (click/screenshot, etc.)
    WAITING_PERMISSION, // Persistently paused, waiting for the Android system permission result
    ERROR            // Error
}
