// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.knowledge.memory

import com.pixelvisionlab.pvautoagent.core.agent.memory.*

import com.pixelvisionlab.pvautoagent.core.knowledge.memory.*

/** Storage operations for the canonical Markdown memory documents. */
interface MemoryDocumentStorage {
    fun readFile(relativePath: String): String?
    fun writeFile(relativePath: String, content: String)
    fun exists(relativePath: String): Boolean
}
