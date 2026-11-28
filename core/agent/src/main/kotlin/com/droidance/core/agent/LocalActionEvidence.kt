// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.agent

import com.droidance.core.agent.*

import com.droidance.core.agent.event.ToolValidationEvidence

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
            // Không xuất semantic_changed khi ngữ nghĩa không quan sát được: khi không có dấu vân tay văn bản,
            // so sánh ngữ nghĩa chỉ còn tên gói/Activity, mà với App nhồi mọi trang vào một Activity (như QQ với
            // SplashActivity chứa danh sách tin nhắn/cửa sổ chat/bảng nhãn dán) thì danh tính trang luôn giống nhau, trường này
            // về mặt vật lý chỉ có thể là false. Báo ra chẳng khác nào biến mỗi lần nhấn thành "không tiến triển", nhấn liền bốn lần chắc chắn bị
            // guard không tiến triển giết. Không đánh giá được thì không báo, để việc xác định không tiến triển lùi về kết quả công cụ, còn hành động lặp lại giao cho
            // bộ đếm không gian độc lập màn hình làm dự phòng.
            if (semanticObservable) parts += "semantic_changed=$semanticChanged"
            if (runtimeProgress) parts += "runtime_progress=true"
            // Không xuất trường boolean goal_progress: state=GOAL_PROGRESS đã diễn đạt "nội dung trang này bị thay thế diện rộng"
            // như một sự thật quan sát được, còn "mục tiêu người dùng đã đạt được chưa" vẫn do sổ bằng chứng và done gate phán định, bộ xác minh cục bộ
            // không đưa ra khẳng định này.
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
