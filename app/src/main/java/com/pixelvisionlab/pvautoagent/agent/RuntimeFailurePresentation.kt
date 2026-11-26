// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.agent

import com.pixelvisionlab.pvautoagent.core.agent.*
import com.pixelvisionlab.pvautoagent.core.device.agent.*

import com.pixelvisionlab.pvautoagent.agent.*

import androidx.annotation.StringRes
import com.pixelvisionlab.pvautoagent.R
import com.pixelvisionlab.pvautoagent.core.model.llm.ProviderErrorCategory
import com.pixelvisionlab.pvautoagent.core.model.llm.providerHttpException
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.DeferredMemoryEnqueueException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

/** Classify failures at the presentation boundary without treating every exception as an API failure. */
@StringRes
fun runtimeFailureMessageResource(error: Throwable): Int {
    val provider = error.providerHttpException()
    return when {
        error is CancellationException -> R.string.execution_cancelled
        error is DeferredMemoryEnqueueException -> R.string.memory_background_save_failed
        error is com.pixelvisionlab.pvautoagent.core.model.llm.OpenAIResponsesIncompleteException ->
            if (error.outputLimitReached) R.string.chat_output_limited else R.string.execution_model_request_failed
        error is com.pixelvisionlab.pvautoagent.core.model.llm.OpenAIResponsesUnterminatedException -> R.string.execution_model_invalid_response
        provider?.category == ProviderErrorCategory.AUTHENTICATION -> R.string.chat_provider_auth_failed
        provider?.category == ProviderErrorCategory.PERMISSION_DENIED -> R.string.chat_provider_permission_denied
        provider?.category == ProviderErrorCategory.RATE_LIMIT -> R.string.chat_provider_rate_limited
        error is UnknownHostException || error is ConnectException -> R.string.chat_network_unavailable
        error is InterruptedIOException -> R.string.execution_model_request_timeout
        provider != null -> R.string.execution_model_request_failed
        else -> R.string.execution_internal_error
    }
}
