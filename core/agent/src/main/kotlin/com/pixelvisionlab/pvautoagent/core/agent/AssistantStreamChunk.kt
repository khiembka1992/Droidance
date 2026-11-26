// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

/** The producing round owns a chunk even if a newer user message has already arrived. */
data class AssistantStreamChunk(val text: String, val roundId: String?)
