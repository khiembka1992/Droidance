// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

/** The only lifecycle states owned by the single user-turn model loop. */
enum class AgentLoopState {
    ACTIVATING_ANDROID,
    PHASE_B,
    WAITING_HUMAN,
    COMPLETED
}
