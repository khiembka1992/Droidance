// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.memory

import com.pixelvisionlab.pvautoagent.core.agent.memory.*
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.*
import com.pixelvisionlab.pvautoagent.core.persistence.memory.*

import com.pixelvisionlab.pvautoagent.memory.*

import com.pixelvisionlab.pvautoagent.core.agent.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Application-owned disk work, independent of the conversation job and model lease. */
@Singleton
class DeferredMemoryWriter @Inject constructor(
    private val queue: DeferredMemoryQueue,
    @ApplicationScope private val scope: CoroutineScope,
    private val debugLog: com.pixelvisionlab.pvautoagent.debug.DebugLogManager
) {
    fun resume() {
        scope.launch(Dispatchers.IO) {
            try { queue.drain() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                queue.reportStorageFailure(error)
                debugLog.appendEvent("Deferred memory storage unavailable: ${error.javaClass.simpleName}")
            }
        }
    }
}
