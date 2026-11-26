// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent.debug

import com.pixelvisionlab.pvautoagent.core.agent.debug.*

interface DebugLogAppender {
    fun appendEvent(event: String)
    fun appendPersistentEvent(stream: String, event: String)
}
