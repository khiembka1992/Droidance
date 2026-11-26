// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.model.llm.session

import com.pixelvisionlab.pvautoagent.core.model.llm.session.*

import kotlinx.serialization.Serializable

@Serializable
enum class ModelTransport {
    OPENAI_RESPONSES,
    GENERIC_CHAT_COMPLETIONS
}
