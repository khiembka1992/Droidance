// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.automation

import com.pixelvisionlab.pvautoagent.core.agent.event.*
import com.pixelvisionlab.pvautoagent.core.persistence.agent.event.*
import com.pixelvisionlab.pvautoagent.core.model.llm.models.*
import com.pixelvisionlab.pvautoagent.core.agent.tools.*
import com.pixelvisionlab.pvautoagent.core.knowledge.tools.*
import com.pixelvisionlab.pvautoagent.core.device.tools.*
import com.pixelvisionlab.pvautoagent.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

/** The current Agent edits scheduler definitions through the existing manager. */
class AutomationTool(
    private val manager: AutomationManager,
    private val store: AutomationStore,
    private val scope: () -> TurnEventScope,
    private val description: () -> String,
    private val requestTime: suspend (TurnEventScope) -> Long
) : Tool {
    override val name = "automation"
    override val tier = ToolTier.CORE
    override val category = ToolCategory.SYSTEM
    private val scheduleOperations = mapOf("op" to listOf("create", "update"))
    private val clockCadences = mapOf("cadence" to listOf("DAILY", "WEEKLY"))
    private val startCadences = mapOf("cadence" to listOf("ONCE", "INTERVAL"))
    private val repeatingCadences = mapOf("cadence" to listOf("DAILY", "WEEKLY", "INTERVAL"))
    override val definition get() = ToolDefinition(name, description(), linkedMapOf(
        "op" to ToolParameter("string", "Automation operation", enumValues = listOf("list", "read", "create", "update", "set_enabled", "delete")),
        "id" to ToolParameter("string", "Exact definition ID", required = false,
            requiredWhen = mapOf("op" to listOf("read", "update", "set_enabled", "delete"))),
        "title" to ToolParameter("string", "User-facing task title", required = false,
            requiredWhen = scheduleOperations, minLength = 1),
        "instruction" to ToolParameter("string", "Work to perform at each occurrence; omit the request to create a schedule",
            required = false, requiredWhen = scheduleOperations, minLength = 1),
        "cadence" to ToolParameter("string", "Schedule type", required = false,
            enumValues = listOf("ONCE", "DAILY", "WEEKLY", "INTERVAL"), requiredWhen = scheduleOperations),
        "hour" to ToolParameter("integer", "Local hour 0..23", required = false, requiredWhen = clockCadences),
        "minute" to ToolParameter("integer", "Local minute 0..59", required = false, requiredWhen = clockCadences),
        "days_of_week" to ToolParameter("array", "ISO weekdays 1..7", required = false,
            items = ToolParameter("integer", "Day"), requiredWhen = mapOf("cadence" to listOf("WEEKLY")), minItems = 1),
        "time_zone" to ToolParameter("string", "Explicit IANA time zone", required = false, requiredWhen = scheduleOperations),
        "run_at" to ToolParameter("string", "First deadline, ISO-8601 with UTC offset. Mutually exclusive with delay_minutes",
            required = false, allowedWhen = startCadences, minLength = 1),
        "delay_minutes" to ToolParameter("integer", "Minutes after the user's message; host computes deadline. Mutually exclusive with run_at",
            required = false, allowedWhen = startCadences),
        "interval_minutes" to ToolParameter("integer", "Interval duration in minutes, 1..5256000", required = false,
            requiredWhen = mapOf("cadence" to listOf("INTERVAL"))),
        "end_at" to ToolParameter("string", "Inclusive repetition end, ISO-8601 with UTC offset",
            required = false, allowedWhen = repeatingCadences, minLength = 1),
        "max_occurrences" to ToolParameter("integer", "Repetition trigger limit; zero means unlimited; includes merged/skipped deadlines",
            required = false, allowedWhen = repeatingCadences),
        "missed_policy" to ToolParameter("string", "Deadlines over one minute late", required = false, enumValues = listOf("RUN_ONCE", "SKIP")),
        "original_request" to ToolParameter("string", "Original user request, separate from the per-run instruction", required = false),
        "enabled" to ToolParameter("boolean", "New enabled state", required = false,
            requiredWhen = mapOf("op" to listOf("set_enabled"))),
        "offset" to ToolParameter("integer", "List offset, default zero", required = false)
    ))

    override suspend fun execute(arguments: String): ToolExecutionResult = try {
        val args = Json.parseToJsonElement(arguments).jsonObject
        fun text(key: String) = args[key]?.jsonPrimitive?.contentOrNull ?: error("MISSING_$key")
        suspend fun schedule() = AutomationScheduleInput.parse(args, requestTime(scope()))
        val result = when (text("op")) {
            "list" -> {
                val definitions = store.definitions.value
                val offset = args["offset"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int ?: 0
                require(offset in 0..definitions.size) { "INVALID_CURSOR" }
                val selected = definitions.drop(offset).take(16)
                buildJsonObject {
                    put("definitions", Json.encodeToJsonElement(selected)); put("next_offset", offset + selected.size)
                    put("has_more", offset + selected.size < definitions.size)
                }
            }
            "read" -> buildJsonObject {
                put("definition", Json.encodeToJsonElement(checkNotNull(store.definition(text("id"))) { "AUTOMATION_NOT_FOUND" }))
                put("recent_occurrences", Json.encodeToJsonElement(store.occurrences.value.filter {
                    it.automationId == text("id") }.takeLast(8)))
            }
            "create" -> {
                val owner = scope()
                val invocation = checkNotNull(currentCoroutineContext()[ToolInvocation]).callId
                val id = UUID.nameUUIDFromBytes("${owner.threadId}:${owner.turnId}:$invocation".toByteArray(Charsets.UTF_8)).toString()
                Json.encodeToJsonElement(manager.create(text("title"), text("instruction"), true, schedule(), id = id, originalRequest = args["original_request"]?.jsonPrimitive?.contentOrNull.orEmpty()))
            }
            "update" -> Json.encodeToJsonElement(checkNotNull(manager.update(text("id"), text("title"), text("instruction"), schedule(), originalRequest = args["original_request"]?.jsonPrimitive?.contentOrNull)) { "AUTOMATION_NOT_FOUND" })
            "set_enabled" -> {
                checkNotNull(store.definition(text("id"))) { "AUTOMATION_NOT_FOUND" }
                manager.setEnabled(text("id"), args.getValue("enabled").jsonPrimitive.boolean)
                Json.encodeToJsonElement(store.definition(text("id")))
            }
            "delete" -> {
                manager.delete(text("id"))
                buildJsonObject { put("id", text("id")); put("deleted", true) }
            }
            else -> error("UNSUPPORTED_OPERATION")
        }
        ToolProtocolResult.success(result.toString())
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: AutomationStartTimeException) {
        ToolProtocolResult.notExecuted(error.code, retryable = true,
            details = buildJsonObject { put("repair", error.repair) })
    } catch (error: Exception) { ToolProtocolResult.failed("AUTOMATION_ERROR", false, error) }
}
