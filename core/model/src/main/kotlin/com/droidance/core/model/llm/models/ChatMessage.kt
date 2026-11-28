// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.model.llm.models

import kotlinx.serialization.Serializable

/**
 * Mô hình tin nhắn trò chuyện thống nhất
 * Dùng để truyền tin nhắn giữa Agent và LLM Provider
 */
@Serializable
data class ChatMessage(
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCall>? = null,    // Lệnh gọi công cụ do LLM trả về
    val toolCallId: String? = null,           // ID tương ứng với kết quả thực thi công cụ
    val imageBase64: String? = null,          // Nội dung ảnh tùy chọn (ảnh chụp màn hình)
    val historicalImage: Boolean = false, // Historical evidence, never the current computer frame
    val imageBase64List: List<String> = emptyList(), // Nội dung nhiều ảnh tùy chọn
    val reasoningContent: String? = null,     // Nội dung suy luận của Provider, gửi lại khi giao thức nhiều lượt cần
    val timestamp: Long = System.currentTimeMillis(),
    /** Request-local cache eligibility; never persisted as conversation content. */
    @kotlinx.serialization.Transient val cacheablePrefix: Boolean = false
)

/**
 * Vai trò tin nhắn
 */
@Serializable
enum class Role {
    SYSTEM,     // system prompt (nội dung SOUL.md)
    USER,       // Tin nhắn người dùng
    ASSISTANT,  // Phản hồi của LLM
    TOOL        // Kết quả gọi công cụ
}

/**
 * Định nghĩa lệnh gọi công cụ
 */
@Serializable
data class ToolCall(
    val id: String,            // ID lệnh gọi công cụ
    val name: String,          // Tên công cụ, ví dụ "screenshot", "click"
    val arguments: String      // Tham số định dạng JSON
)

/**
 * Định nghĩa công cụ (danh sách công cụ gửi cho LLM)
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
)

/**
 * Định nghĩa tham số công cụ
 */
@Serializable
data class ToolParameter(
    val type: String,           // "string", "integer", "boolean" v.v.
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
