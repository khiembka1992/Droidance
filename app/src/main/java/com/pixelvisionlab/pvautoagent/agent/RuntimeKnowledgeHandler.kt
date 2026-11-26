// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.agent

import com.pixelvisionlab.pvautoagent.core.agent.AgentTurnLedger
import com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventStore
import com.pixelvisionlab.pvautoagent.core.agent.event.ConversationEntryKind
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.DurableMemoryApplyResult
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.DurableMemoryStore
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.DurableMemoryUpdate
import com.pixelvisionlab.pvautoagent.core.knowledge.memory.TurnMemoryCandidateProjector
import com.pixelvisionlab.pvautoagent.core.knowledge.skill.SkillEngine
import com.pixelvisionlab.pvautoagent.core.knowledge.skill.SkillReflectionQueue
import com.pixelvisionlab.pvautoagent.debug.DebugLogManager
import com.pixelvisionlab.pvautoagent.memory.ExperienceRepository
import com.pixelvisionlab.pvautoagent.memory.ProgressiveMemoryContextProjector
import com.pixelvisionlab.pvautoagent.memory.SessionIndex
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.*

/** Owns scoped memory writes and completion projections without acquiring a model lease. */
@Singleton
class RuntimeKnowledgeHandler @Inject constructor(
    private val durableMemoryStore: DurableMemoryStore,
    private val sessionIndex: SessionIndex,
    private val experiences: ExperienceRepository,
    val skillEngine: SkillEngine,
    val progressiveMemoryContextProjector: ProgressiveMemoryContextProjector,
    val skillReflectionQueue: SkillReflectionQueue,
    private val eventStore: AgentEventStore,
    private val blobs: com.pixelvisionlab.pvautoagent.core.agent.blob.BlobStore,
    private val debugLogManager: DebugLogManager
) {
    suspend fun selectedSkillContext(
        context: com.pixelvisionlab.pvautoagent.core.agent.TurnSessionContext,
        requests: List<com.pixelvisionlab.pvautoagent.core.agent.RequestedSkill>,
        maxChars: Int,
        requestId: String
    ): String {
        if (requests.isEmpty()) return ""
        val text = context.getOrPutTyped {
            com.pixelvisionlab.pvautoagent.core.knowledge.skill.SkillTurnSession(skillEngine)
        }.preload(requests, maxChars)
        val data = buildJsonObject {
            put("context_chars", text.length)
            put("skills", JsonArray(requests.map { selection -> buildJsonObject {
                put("name", selection.name); put("revision", selection.revision); put("draft", selection.draft)
            } }))
        }
        val blob = blobs.put(data.toString().toByteArray())
        eventStore.append(com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventDraft(
            com.pixelvisionlab.pvautoagent.core.agent.event.AgentEventKind.PROGRAM_EXECUTION, context.threadId, context.turnId,
            payloadJson = buildJsonObject {
                put("phase", "skills_selected"); put("data", data); put("data_ref", blob.contentRef)
                put("artifacts", JsonArray(listOf(buildJsonObject {
                    put("content_ref", blob.contentRef); put("size_bytes", blob.sizeBytes); put("mime_type", "application/json")
                })))
            }.toString(), idempotencyKey = "skills-selected:$requestId"
        ))
        return text
    }

    suspend fun applyUpdates(
        updates: List<DurableMemoryUpdate>,
        active: AgentTurnLedger.ActiveTurn,
        callId: String,
        isCurrent: () -> Boolean
    ): Result<DurableMemoryApplyResult> {
        val ownedUpdates = updates.mapIndexed { index, update -> update.copy(
            operationId = "${active.scope.turnId}:$callId:$index",
            sourceRef = active.scope.turnId
        ) }
        return durableMemoryStore.applyUpdates(ownedUpdates) {
            check(isCurrent()) { "Turn changed" }
            eventStore.threadReadModel(active.scope.threadId)
                ?.conversation?.entries.orEmpty()
                .filter { it.turnId == active.scope.turnId && (it.kind == ConversationEntryKind.USER_MESSAGE || it.kind == ConversationEntryKind.USER_STEER) }
                .map { it.text }.distinct()
        }
    }

    suspend fun finalizeTurn(activeTurn: AgentTurnLedger.ActiveTurn?): Set<String> {
        runCatching {
            if (activeTurn == null) return@runCatching
            val candidate = TurnMemoryCandidateProjector.project(
                events = eventStore.eventsForThread(activeTurn.scope.threadId),
                turnId = activeTurn.scope.turnId
            ) ?: return@runCatching
            sessionIndex.indexCandidate(candidate)
        }.onFailure { error ->
            debugLogManager.appendEvent("Memory candidate projection failed: ${error.message.orEmpty().take(120)}")
        }
        return activeTurn?.let { experiences.finalizeTurn(it.scope.turnId) }.orEmpty()
    }
}
