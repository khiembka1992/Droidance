// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*

fun interface RuntimeModeProvider {
    fun currentMode(): RuntimeMode?
}
