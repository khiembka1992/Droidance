// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.model.llm.models

/**
 * Sự kiện đầu ra streaming
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

    /** Khối nội dung văn bản */
    data class Content(val text: String) : LLMStreamEvent()

    /** Khối lệnh gọi công cụ */
    data class ToolCallChunk(
        val index: Int,          // Chỉ mục trong danh sách công cụ
        val id: String? = null,  // ID lệnh gọi công cụ (chỉ khối đầu tiên có)
        val name: String? = null,// Tên công cụ (chỉ khối đầu tiên có)
        val arguments: String    // Phần tăng dần của tham số
    ) : LLMStreamEvent()

    /** Khối nội dung suy luận của Provider, chỉ dùng để truyền lại theo giao thức, không hiển thị cho người dùng. */
    data class ReasoningChunk(val text: String) : LLMStreamEvent()

    /** Transport activity without retaining or publishing provider reasoning text. */
    data class Activity(val phase: Phase, val textChars: Int = 0) : LLMStreamEvent() {
        enum class Phase { RESPONSE_STARTED, REASONING, REASONING_FINISHED }
    }
}
