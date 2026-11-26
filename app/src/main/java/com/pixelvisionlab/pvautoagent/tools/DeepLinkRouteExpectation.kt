// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.tools

import com.pixelvisionlab.pvautoagent.core.agent.tools.*
import com.pixelvisionlab.pvautoagent.core.knowledge.tools.*
import com.pixelvisionlab.pvautoagent.core.device.tools.*

import com.pixelvisionlab.pvautoagent.tools.*

import com.pixelvisionlab.pvautoagent.config.DeepLinkKnowledgeStore
import java.net.URLEncoder

/** Serializes a catalog landing contract into the action receipt for the generic evidence ledger. */
internal object DeepLinkRouteExpectation {
    fun marker(route: DeepLinkKnowledgeStore.DeepLinkRoute): String? {
        val verification = route.verification
        if (verification.activityContains.isBlank() && verification.textAny.isEmpty()) return null
        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
        val textAny = verification.textAny
            .filter(String::isNotBlank)
            .joinToString(",", transform = ::encode)
        return "[DEEPLINK_ROUTE_EXPECTATION] route_id=${encode(route.id)} " +
            "action=${encode(route.action)} activity_contains=${encode(verification.activityContains)} " +
            "text_any=$textAny"
    }
}
