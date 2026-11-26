// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.model.llm.models

import kotlinx.serialization.Serializable

/**
 * Configuration of large-model reasoning/thinking effort levels
 */
@Serializable
enum class ReasoningEffort(val wireValue: String?) {
    DEFAULT(null),      // Omit the parameter and use the model's default.
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),        // Low / fast response
    MEDIUM("medium"),  // Medium
    HIGH("high"),      // High / deep thinking
    XHIGH("xhigh"),
    MAX("max")
}
