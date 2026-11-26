// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.prompt.StablePrefix
import com.pixelvisionlab.pvautoagent.core.agent.tools.OperatorToolProfile
import javax.inject.Inject
import javax.inject.Singleton

/** Entry point to the single model decision loop. All mutable state belongs to its turn session. */
@Singleton
class TaskCoordinator @Inject internal constructor(private val services: TaskTurnServices) {
    fun stablePrefix(mode: com.pixelvisionlab.pvautoagent.core.agent.device.RuntimeMode?,
        executionSpace: com.pixelvisionlab.pvautoagent.core.agent.device.ExecutionSpace?,
        toolProfile: OperatorToolProfile = OperatorToolProfile.DEFAULT,
        extraSystemContext: String = ""): StablePrefix =
        services.stablePrefix(mode, executionSpace, toolProfile, extraSystemContext)

    suspend fun coordinateTask(
        taskGoal: String,
        additionalContext: String,
        durableTurn: AgentTurnLedger.ActiveTurn,
        turnContext: String,
        expectedBackend: com.pixelvisionlab.pvautoagent.core.agent.attempt.ExecutionBackendObservation?,
        executionStrategy: com.pixelvisionlab.pvautoagent.core.agent.device.DeviceExecutionStrategy?,
        initialScreenRequired: Boolean = false,
        initialScreenFrame: ByteArray? = null,
        toolProfile: OperatorToolProfile = OperatorToolProfile.DEFAULT,
        mode: com.pixelvisionlab.pvautoagent.core.agent.device.RuntimeMode?,
        host: TurnHost = object : TurnHost {}
    ): TurnResult = TaskTurnSession(
        services = services,
        taskGoal = taskGoal,
        additionalContext = additionalContext,
        durableTurn = durableTurn,
        turnContext = turnContext,
        expectedBackend = expectedBackend,
        executionStrategy = executionStrategy,
        initialScreenRequired = initialScreenRequired,
        initialScreenFrame = initialScreenFrame,
        toolProfile = toolProfile,
        mode = mode,
        host = host
    ).execute()
}
