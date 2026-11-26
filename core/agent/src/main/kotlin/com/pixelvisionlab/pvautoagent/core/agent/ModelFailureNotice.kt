// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.event.ConversationEntryKind
import com.pixelvisionlab.pvautoagent.core.agent.event.ConversationEntryReadModel
import com.pixelvisionlab.pvautoagent.core.model.llm.ChatCompletionsStreamFormatException
import com.pixelvisionlab.pvautoagent.core.model.llm.ProviderErrorCategory
import com.pixelvisionlab.pvautoagent.core.model.llm.providerHttpException

/** Classifies transport facts only; provider messages and endpoints never become public text. */
internal fun modelFailureNoticeKey(error: Throwable): String = when (error.providerHttpException()?.category) {
    ProviderErrorCategory.BILLING -> "termination_model_billing"
    ProviderErrorCategory.AUTHENTICATION -> "termination_model_authentication"
    ProviderErrorCategory.PERMISSION_DENIED -> "termination_model_permission_denied"
    ProviderErrorCategory.RATE_LIMIT -> "termination_model_rate_limit"
    ProviderErrorCategory.CONTEXT_LIMIT -> "termination_model_context_limit"
    ProviderErrorCategory.REQUEST_REJECTED, ProviderErrorCategory.PREVIOUS_RESPONSE_NOT_FOUND,
    ProviderErrorCategory.REASONING_UNSUPPORTED, ProviderErrorCategory.IMAGE_DETAIL_UNSUPPORTED -> "termination_model_request_rejected"
    ProviderErrorCategory.SERVER -> "termination_model_server"
    ProviderErrorCategory.MODEL_UNAVAILABLE -> "termination_model_not_found"
    else -> when {
        error is ChatCompletionsStreamFormatException || error is kotlinx.serialization.SerializationException ||
            error is com.pixelvisionlab.pvautoagent.core.model.llm.OpenAIResponsesIncompleteException ||
            error is com.pixelvisionlab.pvautoagent.core.model.llm.OpenAIResponsesUnterminatedException -> "termination_model_response_invalid"
        generateSequence(error) { it.cause }.take(8).any { it is java.io.IOException } -> "termination_model_network"
        else -> "termination_model_unavailable"
    }
}

private const val MODEL_REQUEST_DIAGNOSTIC_PREFIX = "MODEL_REQUEST:"

fun TurnResult.modelRequestFailureDiagnostic(): String? {
    if (terminal != TurnTerminal.SYSTEM_ERROR || !modelFailureSummary.isNullOrBlank()) return null
    if (failureReason != TurnFailureReason.MODEL_UNAVAILABLE && failureReason != TurnFailureReason.MODEL_TIMEOUT) return null
    return MODEL_REQUEST_DIAGNOSTIC_PREFIX + (stopDiagnostic ?: failureReason.name)
}

fun ConversationEntryReadModel.isModelRequestFailureNotice(): Boolean =
    (kind == ConversationEntryKind.TURN_SYSTEM_NOTICE || kind == ConversationEntryKind.TURN_ASSISTANT_REPLY) &&
        (diagnosticCode?.startsWith(MODEL_REQUEST_DIAGNOSTIC_PREFIX) == true || diagnosticCode == "model_unavailable")
