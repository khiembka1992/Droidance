// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.tools

import com.pixelvisionlab.pvautoagent.core.agent.tools.*
import com.pixelvisionlab.pvautoagent.core.knowledge.tools.*
import com.pixelvisionlab.pvautoagent.core.device.tools.*

import com.pixelvisionlab.pvautoagent.core.model.llm.models.ToolDefinition
import javax.inject.Inject
import javax.inject.Singleton

/** Owns workspace and automation tool registration and their scoped storage. */
@Singleton
class WorkspaceToolHandler @Inject constructor(
    private val automationManager: com.pixelvisionlab.pvautoagent.automation.AutomationManager,
    private val automationStore: com.pixelvisionlab.pvautoagent.automation.AutomationStore,
    val taskWorkspace: com.pixelvisionlab.pvautoagent.core.agent.workspace.TaskWorkspace,
    private val programEvents: com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventStore,
    private val ruleConfigManager: com.pixelvisionlab.pvautoagent.config.RuleConfigManager,
    private val toolTurnServices: ToolTurnServices
) {
    fun registerAll(register: (Tool) -> Unit, toolHelp: (String) -> ToolDefinition?, sourceExists: (String) -> Boolean) {
        register(com.pixelvisionlab.pvautoagent.automation.AutomationTool(
            automationManager, automationStore,
            scope = { toolTurnServices.requireCurrent().sessionContext.let {
                com.pixelvisionlab.pvautoagent.core.agent.event.TurnEventScope(it.threadId, it.turnId)
            } },
            description = { ruleConfigManager.readMarkdownSection("automation_tools.md", "description") },
            requestTime = { owner ->
                programEvents.eventsForTurn(owner.turnId).lastOrNull {
                    it.eventKind == com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventKind.USER_MESSAGE ||
                        it.eventKind == com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventKind.USER_STEER
                }?.occurredAt ?: error("SCHEDULE_REQUEST_TIME_MISSING")
            }
        ))
        register(WorkspaceTool(
            workspace = taskWorkspace,
            scope = { toolTurnServices.requireCurrent().sessionContext.let {
                com.pixelvisionlab.pvautoagent.core.agent.event.TurnEventScope(it.threadId, it.turnId)
            } },
            rules = { section -> ruleConfigManager.readMarkdownSection("workspace.md", section) },
            readArtifact = { arguments -> toolTurnServices.requireCurrent().history.read(arguments) },
            toolHelp = { name -> toolHelp(name) },
            sourceExists = sourceExists,
            toolGuidance = { name ->
                if (name in setOf("update_memory", "session_search", "session_read", "experience"))
                    ruleConfigManager.readMarkdownSection("memory_policy.md", "automatic_conversation_memory")
                else ""
            }
        ))

        register(TaskMemoTool(taskWorkspace, scope = {
            toolTurnServices.requireCurrent().sessionContext.let {
                com.pixelvisionlab.pvautoagent.core.agent.event.TurnEventScope(it.threadId, it.turnId)
            }
        }, rules = { section -> ruleConfigManager.readMarkdownSection("workspace.md", section) }))

    }
}
