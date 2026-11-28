// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.model.llm.models

import kotlinx.serialization.Serializable

/**
 * Cấu hình mức độ suy luận/suy nghĩ của mô hình lớn
 */
@Serializable
enum class ReasoningEffort(val wireValue: String?) {
    DEFAULT(null),      // Omit the parameter and use the model's default.
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),        // Thấp / phản hồi nhanh
    MEDIUM("medium"),  // Trung bình
    HIGH("high"),      // Cao / suy nghĩ sâu
    XHIGH("xhigh"),
    MAX("max")
}
