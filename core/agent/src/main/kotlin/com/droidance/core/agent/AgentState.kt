// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.agent

import com.droidance.core.agent.*

enum class AgentState {
    IDLE,            // Rảnh, chờ nhập liệu
    THINKING,        // Đang gọi LLM suy luận
    EXECUTING_TOOL,  // Đang thực thi công cụ (nhấn/ảnh chụp màn hình v.v.)
    WAITING_PERMISSION, // Đã tạm dừng bền vững, chờ kết quả quyền hệ thống Android
    ERROR            // Lỗi
}
