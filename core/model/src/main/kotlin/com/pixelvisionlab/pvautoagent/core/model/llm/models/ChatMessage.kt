// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.model.llm.models

import kotlinx.serialization.Serializable

/**
 * Unified chat message model
 * Used to pass messages between the Agent and LLM Providers
 */
@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall>? = null,    // Tool calls returned by the LLM
    val toolCallId: String? = null,           // ID corresponding to the tool execution result
    val imageBase64: String? = null,          // Optional image content (screenshot)
    val historicalImage: Boolean = false, // Historical evidence, never the current computer frame
    val imageBase64List: List<String> = emptyList(), // Optional multi-image content
    val reasoningContent: String? = null,     // Provider reasoning content, sent back when the multi-turn protocol requires it
    val timestamp: Long = System.currentTimeMillis(),
    /** Request-local cache eligibility; never persisted as conversation content. */
    @kotlinx.serialization.Transient val cacheablePrefix: Boolean = false
)

/**
 * Message role
 */
@Serializable
enum class Role {
    SYSTEM,     // System prompt (SOUL.md content)
    USER,       // User message
    ASSISTANT,  // LLM reply
    TOOL        // Tool call result
}

/**
 * Tool call definition
 */
@Serializable
data class ToolCall(
    val id: String,            // Tool call ID
    val name: String,          // Tool name, such as "screenshot", "click"
    val arguments: String      // Arguments in JSON format
)

/**
 * Tool definition (the tool list sent to the LLM)
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
)

/**
 * Tool parameter definition
 */
@Serializable
data class ToolParameter(
    val type: String,           // "string", "integer", "boolean", etc.
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
    val items: ToolParameter? = null,
    val properties: Map<String, ToolParameter>? = null,
    val additionalProperties: Boolean? = null,
    val additionalPropertySchema: ToolParameter? = null,
    /** Optional metadata validated by its consumer after dispatch, independently of the main effect. */
    val deferredValidation: Boolean = false,
    /** Required when any named sibling selector has one of these protocol values. */
    val requiredWhen: Map<String, List<String>> = emptyMap(),
    /** Present only for the listed values of every named sibling selector. */
    val allowedWhen: Map<String, List<String>> = emptyMap(),
    /** JSON Schema string length in Unicode code points, not UTF-16 code units. */
    val minLength: Int? = null,
    /** JSON Schema minimum number of array elements. */
    val minItems: Int? = null
)
