// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.model.llm.models

/**
 * Streaming output event
 */
sealed class LLMStreamEvent {
    /** Local transport facts, not provider activity or proof of the model's internal effort. */
    data class ReasoningConfiguration(
        val requested: ReasoningEffort,
        val sent: ReasoningEffort,
        val fallbackReason: String?,
        val supportedEfforts: List<ReasoningEffort>
    ) : LLMStreamEvent()

    /** Local facts about the request actually sent, such as continuation and extra fields. */
    data class RequestConfiguration(val facts: Map<String, String>) : LLMStreamEvent()

    /** Wire-reported metadata; absent usage/model values remain unknown. */
    data class ResponseMetadata(
        val returnedModel: String?,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val cachedInputTokens: Int? = null,
        val reasoningTokens: Int? = null,
        val finishReason: String? = null,
        val cacheWriteTokens: Int? = null
    ) : LLMStreamEvent()

    /** Text content chunk */
    data class Content(val text: String) : LLMStreamEvent()

    /** Tool call chunk */
    data class ToolCallChunk(
        val index: Int,          // Index in the tool list
        val id: String? = null,  // Tool call ID (present only in the first chunk)
        val name: String? = null,// Tool name (present only in the first chunk)
        val arguments: String    // Incremental part of the arguments
    ) : LLMStreamEvent()

    /** Provider reasoning content chunk, used only for the protocol round-trip and never shown to the user. */
    data class ReasoningChunk(val text: String) : LLMStreamEvent()

    /** Transport activity without retaining or publishing provider reasoning text. */
    data class Activity(val phase: Phase, val textChars: Int = 0) : LLMStreamEvent() {
        enum class Phase { RESPONSE_STARTED, REASONING, REASONING_FINISHED }
    }
}
