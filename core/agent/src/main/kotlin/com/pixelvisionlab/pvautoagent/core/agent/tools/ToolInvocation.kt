// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent.tools

import com.pixelvisionlab.pvautoagent.core.agent.tools.*

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Immutable identity inherited only by work belonging to this tool invocation. */
class ToolInvocation(val callId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ToolInvocation>
}
