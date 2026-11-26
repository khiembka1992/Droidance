// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.knowledge.memory

import com.pixelvisionlab.pvautoagent.core.agent.memory.*

import com.pixelvisionlab.pvautoagent.core.knowledge.memory.*

data class MemoryEditSnapshot(
    val sourceDocument: String,
    val displayText: String
)

enum class MemoryDisplaySaveResult {
    SAVED,
    STALE,
    AMBIGUOUS
}
