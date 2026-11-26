// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.screens

import com.pixelvisionlab.pvautoagent.automation.AutomationDefinition
import com.pixelvisionlab.pvautoagent.automation.AutomationDeliveryState
import com.pixelvisionlab.pvautoagent.automation.AutomationExecutionState
import com.pixelvisionlab.pvautoagent.automation.AutomationOccurrence

internal enum class AutomationPlanState { ACTIVE, PAUSED, ENDED }
internal enum class AutomationListFilter { ALL, ACTIVE, PAUSED, ENDED }

internal fun automationPlanState(definition: AutomationDefinition): AutomationPlanState = when {
    definition.finishedAtMs > 0L -> AutomationPlanState.ENDED
    definition.enabled -> AutomationPlanState.ACTIVE
    else -> AutomationPlanState.PAUSED
}

internal fun matchesAutomationFilter(definition: AutomationDefinition, filter: AutomationListFilter): Boolean =
    when (filter) {
        AutomationListFilter.ALL -> true
        AutomationListFilter.ACTIVE -> automationPlanState(definition) == AutomationPlanState.ACTIVE
        AutomationListFilter.PAUSED -> automationPlanState(definition) == AutomationPlanState.PAUSED
        AutomationListFilter.ENDED -> automationPlanState(definition) == AutomationPlanState.ENDED
    }

/** Only ongoing work or a problem needs an extra line in the task list. */
internal fun automationListExecutionState(latest: AutomationOccurrence?): AutomationExecutionState? =
    latest?.executionState?.takeUnless {
        it in setOf(AutomationExecutionState.SUCCEEDED, AutomationExecutionState.SKIPPED, AutomationExecutionState.CANCELLED)
    }

internal fun automationDeliveryNeedsAttention(latest: AutomationOccurrence?): Boolean =
    latest?.deliveryState == AutomationDeliveryState.FAILED
