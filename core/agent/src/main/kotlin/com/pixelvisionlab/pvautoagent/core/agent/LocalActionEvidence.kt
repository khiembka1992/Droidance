// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

import com.pixelvisionlab.pvautoagent.core.agent.event.ToolValidationEvidence

/** Observation and action validation facts shared across execution implementations. */
object LocalActionEvidence {
    enum class ProgressState {
        ACTION_CONFIRMED,
        RAW_CHANGED,
        STATE_CHANGED,
        GOAL_PROGRESS,
        CHANGED,
        UNCHANGED,
        CONTEXT_CHANGED,
        PARTIAL,
        ROUTE_LAUNCHED_UNVERIFIED,
        ROUTE_VERIFIED,
        ROUTE_FAILED,
        UNKNOWN
    }

    data class TextFingerprint(
        val topTexts: List<String> = emptyList(),
        val bottomTexts: List<String> = emptyList(),
        val allTextHash: Int = 0,
        val lineCount: Int = 0
    )

    data class ObservationLite(
        val screenHash: Long?,
        val foregroundPackage: String?,
        val visualFingerprint: Long? = null,
        val textFingerprint: TextFingerprint? = null,
        val semanticState: ScreenSemanticState? = null,
        val observationId: String? = null,
        val backendSessionId: String? = null,
        val displayId: Int? = null,
        val capturedAtMs: Long = System.currentTimeMillis()
    )

    data class ValidationResult(
        val toolName: String,
        val progressState: ProgressState,
        val reason: String,
        val beforeHash: Long?,
        val afterHash: Long?,
        val foregroundPackage: String?,
        val expectedPackage: String? = null,
        val noProgressCount: Int = 0,
        val rawChanged: Boolean = false,
        val semanticChanged: Boolean = false,
        val semanticObservable: Boolean = true,
        val runtimeProgress: Boolean = false,
        val goalProgress: Boolean = false,
        val screenSemanticState: ScreenSemanticState? = null,
        val visualFingerprint: Long? = null
    ) {
        fun toEvidence(): ToolValidationEvidence = ToolValidationEvidence(
            progressState = progressState.name,
            reason = reason,
            foregroundPackage = foregroundPackage,
            expectedPackage = expectedPackage,
            noProgressCount = noProgressCount,
            rawChanged = rawChanged,
            semanticChanged = semanticChanged.takeIf { semanticObservable },
            runtimeProgress = runtimeProgress,
            goalProgress = goalProgress || progressState == ProgressState.GOAL_PROGRESS,
            pageKey = screenSemanticState?.pageKey,
            goalStage = screenSemanticState?.goalStage,
            routeKey = screenSemanticState?.routeKey(actionIntent = null, targetKey = null, normalizedTextIntent = null),
            visualFingerprint = visualFingerprint
        )

        fun toToolLine(): String {
            val parts = mutableListOf<String>()
            parts += "state=$progressState"
            parts += "reason=$reason"
            parts += "raw_changed=$rawChanged"
            // When semantics are unobservable, semantic_changed is not emitted: without a text fingerprint,
            // the semantic comparison is left with only the package/Activity, and in an App that stuffs every page into one Activity (QQ's
            // SplashActivity hosts the message list/chat window/emoji panel) page identity is constant, so this field
            // is physically always false. Emitting it would feed every click in as "no progress", and four clicks in a row would inevitably be
            // killed by the no-progress guard. If it cannot be judged, do not report it; let the no-progress decision fall back to the tool result, and leave repeated actions to
            // the screen-independent spatial counter as a backstop.
            if (semanticObservable) parts += "semantic_changed=$semanticChanged"
            if (runtimeProgress) parts += "runtime_progress=true"
            // The goal_progress boolean field is not emitted: state=GOAL_PROGRESS already expresses "this page's content was largely replaced"
            // as an observable fact, while "whether the user's goal has been achieved" is still decided by the evidence ledger and the done gate; the local validator
            // does not make that assertion.
            if (!foregroundPackage.isNullOrBlank()) parts += "foreground=$foregroundPackage"
            if (!expectedPackage.isNullOrBlank()) parts += "target=$expectedPackage"
            if (noProgressCount > 0) parts += "noProgress=$noProgressCount"
            // route_key repeats foreground/page_key/goal_stage and visual_fingerprint is a local
            // comparison value; both stay in the structured evidence, not in the model-facing line.
            screenSemanticState?.let { state ->
                parts += "page_key=${state.pageKey}"
                if (!state.goalStage.isNullOrBlank()) parts += "goal_stage=${state.goalStage}"
            }
            return "[LOCAL_VALIDATION] " + parts.joinToString("; ")
        }
    }
}
