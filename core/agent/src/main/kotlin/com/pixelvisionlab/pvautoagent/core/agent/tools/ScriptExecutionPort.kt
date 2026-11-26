// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent.tools

import com.pixelvisionlab.pvautoagent.core.agent.tools.*

import com.pixelvisionlab.pvautoagent.core.agent.event.ToolExecutionResult
import com.pixelvisionlab.pvautoagent.core.model.llm.models.ToolCall

/** Device operations needed by compiled skill execution. */
interface ScriptExecutionPort {
    suspend fun execute(toolCall: ToolCall): ToolExecutionResult
    fun invalidatePendingObservation()
    fun consumePendingObservation(): ObservationEnvelope?
    fun bindModelFrame(observation: ObservationEnvelope)
}
