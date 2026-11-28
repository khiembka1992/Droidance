// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.agent

import com.droidance.core.agent.workspace.TaskWorkspace

import com.droidance.core.agent.harness.DurableModelRoundCoordinator
import com.droidance.core.agent.harness.ToolContractCatalog
import com.droidance.core.agent.context.DurableContextCheckpointCoordinator
import com.droidance.core.agent.prompt.StablePrefix
import com.droidance.core.agent.prompt.StablePrefixBuilder
import com.droidance.core.agent.event.ToolExecutionResult
import com.droidance.core.agent.event.AgentEventStore
import com.droidance.core.agent.blob.BlobStore
import com.droidance.core.agent.ports.AgentConfiguration
import com.droidance.core.agent.debug.DebugLogAppender
import com.droidance.core.model.llm.ModelClient
import com.droidance.core.model.llm.models.ToolCall
import com.droidance.core.model.llm.models.ToolDefinition
import com.droidance.core.agent.tools.ToolCatalog
import com.droidance.core.agent.ports.ToolExecutorPort
import com.droidance.core.agent.tools.OperatorToolProfile
import com.droidance.core.agent.tools.UnifiedDeviceToolProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TaskTurnServices @Inject constructor(
    internal val llmRouter: ModelClient,
    internal val toolExecutor: ToolExecutorPort,
    internal val debugLogManager: DebugLogAppender,
    internal val evidenceLedger: TaskEvidenceLedger,
    internal val evidenceRecorder: OperatorEvidenceRecorder,
    internal val observationFreshnessPolicy: ObservationFreshnessPolicy,
    internal val ruleConfigManager: AgentConfiguration,
    internal val latencyTracker: com.droidance.core.agent.debug.TaskLatencyTracker,
    internal val toolContractCatalog: ToolContractCatalog,
    internal val durableModelRoundCoordinator: DurableModelRoundCoordinator,
    internal val taskContextCompactor: TaskContextCompactor,
    internal val durableContextCheckpointCoordinator: DurableContextCheckpointCoordinator,
    internal val eventStore: AgentEventStore,
    internal val blobStore: BlobStore,
    internal val runtimeInfo: com.droidance.core.agent.ports.TaskRuntimeInfo
    ) {
    internal val MAX_TASK_NOTE_CHARS = 4000
    internal val workspaceContextReader = TaskWorkspace(eventStore, blobStore)

    internal val modelContextAssembler = com.droidance.core.agent.context.ModelContextAssembler(
        maxTaskNoteChars = MAX_TASK_NOTE_CHARS,
        pureVlmMode = com.droidance.core.agent.tools.ObservationModes.PURE_VLM,
        text = { key, values ->
            values.entries.fold(ruleConfigManager.readMarkdownSection("model_context.md", key)) { template, (name, value) ->
                template.replace("{$name}", value)
            }
        }
    )

    internal data class OperatorTooling(
        val tools: List<ToolDefinition>,
        val contentTools: List<ToolDefinition>,
        val scheduler: OperatorRoundScheduler,
        val completionGroundingByTool: Map<String, Boolean>,
        val registeredTools: List<com.droidance.core.agent.harness.RegisteredHarnessTool>
    )

    /**
     * Captures the exact Operator tool schema used by this turn as part of its stable
     * model-session identity. Context budgeting must count the same schema that the
     * provider receives; an empty placeholder would undercount every long task.
     */
    fun stablePrefix(
        mode: com.droidance.core.agent.device.RuntimeMode?,
        executionSpace: com.droidance.core.agent.device.ExecutionSpace?,
        toolProfile: OperatorToolProfile = OperatorToolProfile.DEFAULT,
        extraSystemContext: String = ""
    ): StablePrefix {
        val tooling = buildOperatorTooling(
            mode,
            executionSpace,
            ruleConfigManager.harnessPrompts().communicationContract,
            toolProfile
        )
        return StablePrefixBuilder.build(
            systemPrompt = trustedOperatorSystemPrompt(extraSystemContext),
            tools = tooling.tools,
            indexEntries = emptyList()
        )
    }

    internal val operatorStreamCollector = OperatorStreamCollector(llmRouter) {
        debugLogManager.appendEvent(it)
    }

    internal fun buildOperatorTooling(
        mode: com.droidance.core.agent.device.RuntimeMode?,
        executionSpace: com.droidance.core.agent.device.ExecutionSpace?,
        communication: com.droidance.core.agent.config.CommunicationContract,
        toolProfile: OperatorToolProfile = OperatorToolProfile.DEFAULT
    ): OperatorTooling {
        val schemaPolicy = Json.decodeFromString<com.droidance.core.agent.tools.ToolSchemaContextPolicy>(
            ruleConfigManager.readMarkdownSection("tool_context.md", "schema_context_policy"))
        val baseTools = com.droidance.core.agent.tools.ToolSchemaContext.omitParameterDocumentation(
            toolExecutor.getOperatorToolDefinitions(mode, executionSpace, toolProfile), schemaPolicy)
        val registeredOperatorTools = toolContractCatalog.resolve(
            toolExecutor.getOperatorTools(mode, executionSpace, toolProfile)
        )
        val tools = ActionControlProtocol.addControlParameters(
            tools = GroundedActionProtocol.addScreenEvidenceParameter(
                tools = baseTools,
                parameterDescription = communication.screenEvidenceParameter,
                applyTo = ToolCatalog.screenEvidenceToolNames
            ),
            descriptions = ActionControlProtocol.Descriptions(
                evidenceRefs = communication.evidenceRefsParameter,
                terminalObservationRef = communication.terminalObservationRefParameter,
                claims = ruleConfigManager.readMarkdownSection("completion_recovery.md", "result_entries_parameter"),
                claimResult = ruleConfigManager.readMarkdownSection("completion_recovery.md", "result_text_parameter"),
                unresolvedRequirements = ruleConfigManager.readMarkdownSection("completion_recovery.md", "unresolved_requirements_parameter"),
                requirementChecks = ruleConfigManager.readMarkdownSection("completion_recovery.md", "requirement_checks_parameter")
            ),
            completionTools = ToolCatalog.finalizationToolNames
        )
        val actionTitledTools = ActionTitleProtocol.addParameter(
            ActionControlProtocol.submissionTools(tools), ruleConfigManager.readMarkdownSection("agent_core.md", "action_title_parameter")
                .replace("{language}", ruleConfigManager.outputLanguage()))
        val metadataCarriers = actionTitledTools.filter { schemaPolicy.turnMetadataTools?.contains(it.name) ?: true }
        val carriersWithMetadata = TaskDeadlineProtocol.addParameter(ConversationTitleProtocol.addParameter(TaskTitleProtocol.addParameter(
            metadataCarriers,
            ruleConfigManager.readMarkdownSection("agent_core.md", "task_title_parameter")
                .replace("{language}", ruleConfigManager.outputLanguage())),
            ruleConfigManager.readMarkdownSection("conversation_title.md", "tool_parameter")),
            ruleConfigManager.readMarkdownSection("agent_core.md", "task_deadline_parameter")).associateBy { it.name }
        val titledTools = actionTitledTools.map { carriersWithMetadata[it.name] ?: it }
        // Effort hints are offered only when the provider model accepts an adaptive level.
        // The effort hint rides on the same task-opening tools as the other turn metadata.
        val submissionTools = if (adaptiveReasoningAvailable()) {
            val hinted = ReasoningEffortProtocol.addParameter(metadataCarriers,
                ruleConfigManager.readMarkdownSection("agent_core.md", "next_reasoning_effort_parameter"),
                reasoningControlPolicy()).associate { it.name to it.parameters.getValue(ReasoningEffortProtocol.ARGUMENT) }
            titledTools.map { tool -> hinted[tool.name]?.let { tool.copy(parameters = tool.parameters +
                (ReasoningEffortProtocol.ARGUMENT to it)) } ?: tool }
        } else titledTools
        return OperatorTooling(
            tools = com.droidance.core.agent.tools.ToolSchemaContext.compact(submissionTools, schemaPolicy),
            contentTools = tools,
            scheduler = OperatorRoundScheduler(
                registrations = registeredOperatorTools.map { it.contract },
                capabilityRequirements = registeredOperatorTools.mapNotNull { it.capabilityRequirement }
            ),
            completionGroundingByTool = registeredOperatorTools.associate {
                it.contract.name to it.groundsCompletion
            },
            registeredTools = registeredOperatorTools
        )
    }

    internal fun reasoningControlPolicy(): ReasoningControlPolicy = Json.decodeFromString<ReasoningControlPolicy>(
        ruleConfigManager.readMarkdownSection("agent_core.md", "reasoning_control_policy")
    ).also { it.validate() }

    internal fun adaptiveReasoningAvailable(): Boolean =
        llmRouter.explicitReasoningEfforts().any { it in reasoningControlPolicy().adaptiveEfforts }

    /** System prompt duy nhất của Operator: giao thức thực thi chung + ngôn ngữ đầu ra. */
    internal fun operatorSystemPrompt(): String = ruleConfigManager.operatorSystemPrompt() + "\n\n" +
        ruleConfigManager.readMarkdownSection("tool_context.md", "contract") +
        if (adaptiveReasoningAvailable()) "\n\n" + ruleConfigManager.readMarkdownSection("agent_core.md", "reasoning_control") else ""

    internal fun trustedOperatorSystemPrompt(extraSystemContext: String): String {
        val operatorPrompt = operatorSystemPrompt() + "\n\n" +
            ruleConfigManager.readMarkdownSection("conversation_title.md", "policy")
        return if (extraSystemContext.isNotBlank()) {
            "<trusted_system_instruction>\n$operatorPrompt\n\n$extraSystemContext\n</trusted_system_instruction>"
        } else {
            "<trusted_system_instruction>\n$operatorPrompt\n</trusted_system_instruction>"
        }
    }

    internal fun guardText(key: String, values: Map<String, String> = emptyMap()): String {
        val template = ruleConfigManager.message(key)
        return values.entries.fold(template) { acc, (name, value) ->
            acc.replace("{$name}", value)
        }
    }

    internal fun fallbackText(key: String, values: Map<String, String> = emptyMap()): String {
        val template = ruleConfigManager.publicFallback(key)
        return values.entries.fold(template) { text, (name, value) ->
            text.replace("{$name}", value)
        }
    }

    internal fun screenshotMimeType(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0].toInt() and 0xff == 0x89 &&
            bytes[1].toInt() and 0xff == 0x50 &&
            bytes[2].toInt() and 0xff == 0x4e &&
            bytes[3].toInt() and 0xff == 0x47 -> "image/png"
        bytes.size >= 2 &&
            bytes[0].toInt() and 0xff == 0xff &&
            bytes[1].toInt() and 0xff == 0xd8 -> "image/jpeg"
        else -> "image/png"
    }

    internal fun jsonStringArg(arguments: String, vararg keys: String): String? {
        val obj = runCatching { Json.parseToJsonElement(arguments).jsonObject }.getOrNull() ?: return null
        return keys.firstNotNullOfOrNull { key ->
            obj[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    internal fun isScreenObservation(toolCall: ToolCall): Boolean =
        UnifiedDeviceToolProtocol.isScreenObservation(toolCall.name, toolCall.arguments)

    /**
     * Vòng lặp mô hình duy nhất thực thi mục tiêu hiện tại của người dùng.
     *
     * @param taskGoal Mục tiêu hiện tại của người dùng
     * @param additionalContext Ngữ cảnh bổ sung trong cuộc trò chuyện hiện tại liên quan trực tiếp đến mục tiêu này
     * @param onStreamContent Callback xuất streaming
     * @param stateFlow Luồng trạng thái của Agent (dùng để cập nhật UI)
     * @param onActionUpdate Callback mô tả hành động trước khi công cụ thực thi
     * @param onTaskTitle Tiêu đề ngắn cố định của tác vụ hiện tại, phát một lần sau khi phản hồi bình thường được gửi đi
     * @param onManualTakeoverNeeded Mở lối tiếp quản thủ công khi mô hình nhận ra ranh giới can thiệp thật của người dùng
     * @param waitIfPaused Callback kiểm tra tạm dừng
     * @param takeManualResumeInstruction Khi khôi phục sau khi người dùng tiếp quản, lấy một lần gợi ý khôi phục và bỏ các hành động cũ trước khi tạm dừng
     */

    internal fun promptValuePreview(value: String, maxChars: Int): String {
        return value.replace(Regex("\\s+"), " ").trim().take(maxChars)
    }

    /**
     * Promote the explicitly public instruction/reason when the model omits its reply.
     * The planner validates the proposal before committing a public handoff; rejected calls
     * remain tool feedback for the same Agent, while accepted handoffs require human resume.
     */
    internal fun interventionPublicReply(toolCalls: List<ToolCall>, publicText: String): String? {
        val intervention = toolCalls.firstOrNull { it.name == "ask_human" } ?: return null
        if (publicText.isNotBlank()) return publicText
        val arguments = runCatching {
            Json.parseToJsonElement(intervention.arguments).jsonObject
        }.getOrNull() ?: return guardText("user_intervention_invalid_request")
        return sequenceOf("instruction", "reason")
            .mapNotNull { key -> (arguments[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() }
            .firstOrNull(String::isNotBlank) ?: guardText("user_intervention_invalid_request")
    }

    /**
     * Whether the paired shell control channel is unavailable.
     * Accepts structured tool codes and the input backend's current channel marker.
     */
    internal fun looksLikeRootUnavailable(result: ToolExecutionResult): Boolean =
        result.diagnosticCode == "CHANNEL_DOWN"

    internal fun isLlmTransportTimeout(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is InterruptedIOException ||
                current is java.net.SocketTimeoutException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    internal companion object {
        /** Số mục tường thuật hành động được giữ: đủ để mô hình thấy rõ "đã thử những gì", mà không làm ngữ cảnh phình to. */
        const val ACTION_NARRATIVE_LIMIT = 8

    }

    /**
     * Tạo nhãn thao tác để cửa sổ nổi hiển thị thời gian thực
     * Mục tiêu: để người dùng thấy trực quan trên cửa sổ nổi "Agent hiện đang làm gì"
     */
    internal fun buildActionLabel(tc: ToolCall): String {
        val operation = UnifiedDeviceToolProtocol.operation(tc.name, tc.arguments)
        return if (operation == null) tc.name else "${tc.name}.$operation"
    }

}

internal fun decodeModelFrame(base64: String): ByteArray? {
    val payload = if (base64.startsWith("data:")) base64.substringAfter(',') else base64
    return runCatching { java.util.Base64.getDecoder().decode(payload) }.getOrNull()
}
