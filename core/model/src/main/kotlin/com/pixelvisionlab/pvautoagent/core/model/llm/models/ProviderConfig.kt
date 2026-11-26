// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.model.llm.models

import com.pixelvisionlab.pvautoagent.core.model.llm.session.ModelTransport
import kotlinx.serialization.Serializable

/**
 * Custom LLM Provider configuration
 * Allows the user to add any number of large-model providers
 */
@Serializable
data class ProviderConfig(
    val id: String,                          // Unique identifier (UUID)
    val name: String,                        // User-defined display name
    val apiType: ApiType,                    // API protocol type explicitly chosen by the user
    val transportKind: ModelTransport? = null, // Actual request Adapter; CUSTOM must specify it explicitly
    val transportDiagnostic: ProviderTransportDiagnostic? = null,
    val baseUrl: String,                     // API address
    val apiKey: String = "",                 // API Key (optional; some local models do not need one)
    val models: List<String> = emptyList(),  // List of model IDs available under this Provider
    val defaultModel: String = "",           // Default model
    val isEnabled: Boolean = true,           // Whether enabled
    val contextWindowTokens: Int = 128_000,  // Context window of this model (tokens); the auto-compaction threshold is set from this and varies by model (e.g. set 1_000_000 for a 1M model)
    /** Null lets the same Agent select effort; DEFAULT explicitly keeps provider defaults. */
    val reasoningPreference: ReasoningEffort? = null,
    /** Links an address, including a custom address, to its provider preset group. */
    val providerGroupId: String? = null
) {
    /**
     * Returns an executable transport only when the protocol and Adapter choice match exactly.
     * It does not guess the protocol of old configurations here; unconfirmed entries must be re-confirmed on the settings page before they can be enabled.
     */
    fun resolvedTransportOrNull(): ModelTransport? {
        if (transportDiagnostic != null) return null
        val configured = transportKind ?: return null
        return when (apiType) {
            ApiType.OPENAI_RESPONSES -> configured.takeIf { it == ModelTransport.OPENAI_RESPONSES }
            ApiType.OPENAI_COMPATIBLE -> configured.takeIf {
                it == ModelTransport.GENERIC_CHAT_COMPLETIONS
            }
            ApiType.CUSTOM -> configured.takeIf {
                it == ModelTransport.OPENAI_RESPONSES ||
                    it == ModelTransport.GENERIC_CHAT_COMPLETIONS
            }
        }
    }

    fun isTransportConfigured(): Boolean = resolvedTransportOrNull() != null

    fun isAvailableForRequests(): Boolean = isEnabled && baseUrl.isNotBlank() && isTransportConfigured()
}

/**
 * API protocol type
 * Different APIs use different request/response formats
 */
@Serializable
enum class ApiType {
    OPENAI_RESPONSES,
    OPENAI_COMPATIBLE,
    CUSTOM
}

/** Unconfirmed migration entries must remain in the settings as a structured state; their request protocol must not be guessed. */
@Serializable
enum class ProviderTransportDiagnostic {
    TRANSPORT_CONFIRMATION_REQUIRED
}
