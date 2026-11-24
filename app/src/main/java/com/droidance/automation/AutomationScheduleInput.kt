// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.automation

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.*

internal class AutomationStartTimeException(val code: String, val repair: String) : IllegalArgumentException(code)

/** Shared protocol for model drafts and the Agent tool; relative offsets use a host-owned clock. */
object AutomationScheduleInput {
    fun parse(value: JsonObject, referenceTimeMs: Long): AutomationSchedule {
        fun text(key: String) = value[key]?.jsonPrimitive?.contentOrNull
        fun number(key: String, default: Long = 0L): Long = value[key]?.let {
            if (it is JsonNull) default else requireNotNull(it.jsonPrimitive.longOrNull) { "INVALID_$key" }
        } ?: default
        val cadence = AutomationCadence.valueOf(requireNotNull(text("cadence")).uppercase())
        val runAtInput = text("run_at")
        val delayInput = value["delay_minutes"]?.takeUnless { it is JsonNull }
        val usesStartTime = cadence == AutomationCadence.ONCE || cadence == AutomationCadence.INTERVAL
        val startTimeRepair = "Provide exactly one of run_at or delay_minutes."
        if (runAtInput != null && delayInput != null) {
            throw AutomationStartTimeException("CONFLICTING_START_TIME", startTimeRepair)
        }
        if (usesStartTime && runAtInput == null && delayInput == null) {
            throw AutomationStartTimeException("MISSING_START_TIME", startTimeRepair)
        }
        val absolute = try {
            runAtInput?.let { Instant.parse(it).toEpochMilli() }
        } catch (_: DateTimeParseException) {
            throw AutomationStartTimeException("INVALID_RUN_AT", "Use an ISO-8601 date and time with an explicit UTC offset.")
        } catch (_: ArithmeticException) {
            throw AutomationStartTimeException("INVALID_RUN_AT", "Provide a deadline within the supported date range.")
        }
        val delay = delayInput?.let {
            it.jsonPrimitive.longOrNull?.takeIf { minutes -> minutes >= 0 }
                ?: throw AutomationStartTimeException("INVALID_DELAY", "Use a nonnegative whole number of minutes.")
        }
        require(usesStartTime || (absolute == null && delay == null)) {
            "START_TIME_REQUIRES_ONCE_OR_INTERVAL"
        }
        require(cadence == AutomationCadence.INTERVAL || number("interval_minutes") == 0L) { "INTERVAL_REQUIRES_INTERVAL_CADENCE" }
        val runAt = try {
            absolute ?: delay?.let { Math.addExact(referenceTimeMs, Math.multiplyExact(it, 60_000L)) } ?: 0L
        } catch (_: ArithmeticException) {
            throw AutomationStartTimeException("INVALID_DELAY", "Provide a delay within the supported date range.")
        }
        return AutomationSchedule(
            cadence = cadence,
            hour = if (cadence in setOf(AutomationCadence.DAILY, AutomationCadence.WEEKLY))
                requireNotNull(value["hour"]?.jsonPrimitive?.intOrNull) else 0,
            minute = if (cadence in setOf(AutomationCadence.DAILY, AutomationCadence.WEEKLY))
                requireNotNull(value["minute"]?.jsonPrimitive?.intOrNull) else 0,
            daysOfWeek = value["days_of_week"]?.takeUnless { it is JsonNull }?.jsonArray
                ?.map { requireNotNull(it.jsonPrimitive.intOrNull) }?.toSet().orEmpty(),
            timeZoneId = requireNotNull(text("time_zone")),
            runAtMs = runAt,
            intervalMinutes = number("interval_minutes"),
            endAtMs = text("end_at")?.let { Instant.parse(it).toEpochMilli() } ?: 0L,
            maxOccurrences = number("max_occurrences"),
            missedPolicy = text("missed_policy")?.let { AutomationMissedPolicy.valueOf(it.uppercase()) }
                ?: AutomationMissedPolicy.RUN_ONCE
        )
    }
}
