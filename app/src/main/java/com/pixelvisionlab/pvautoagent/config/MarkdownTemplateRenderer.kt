// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.config

import com.pixelvisionlab.pvautoagent.core.agent.config.*
import com.pixelvisionlab.pvautoagent.core.device.config.*

import com.pixelvisionlab.pvautoagent.config.*

object MarkdownTemplateRenderer {
    fun render(template: String, replacements: Map<String, String>): String {
        return replacements.entries.fold(template) { content, (key, value) ->
            content.replace("{{$key}}", value)
        }.trim()
    }
}
