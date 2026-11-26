// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent

import com.pixelvisionlab.pvautoagent.core.agent.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ObservationTextExtractor {
    fun extract(raw: String, preserveCoordinates: Boolean = false): List<String> {
        if (raw.trimStart().startsWith(PURE_VISION_TRANSPORT_MARKER)) {
            return emptyList()
        }
        // observe_screen now centers on the <untrusted_screen_content> readable text block (CUA single view),
        // so extract from there first; the old format (bare JSON) falls back to structuredTexts, and plain text lines are the final fallback.
        readableContent(raw, preserveCoordinates)?.takeIf { it.isNotEmpty() }?.let { return it }
        structuredTexts(raw)?.takeIf { it.isNotEmpty() }?.let { return it }
        return raw.lineSequence()
            .mapNotNull { cleanVisibleLine(it, preserveCoordinates) }
            .distinct()
            .toList()
    }

    private fun readableContent(raw: String, preserveCoordinates: Boolean): List<String>? {
        val open = "<untrusted_screen_content>"
        val close = "</untrusted_screen_content>"
        val start = raw.indexOf(open)
        if (start < 0) return null
        val end = raw.indexOf(close, start)
        if (end < 0) return null
        return raw.substring(start + open.length, end)
            .lineSequence()
            .filterNot { it.trim().startsWith("[") }  // skip descriptive headers such as [screen text…]/[AIR…]
            .mapNotNull { cleanVisibleLine(it, preserveCoordinates) }
            .distinct()
            .toList()
    }

    private fun structuredTexts(raw: String): List<String>? {
        // readableContent already handles the new format with text blocks; here we only accept the old format that really starts with a JSON object.
        // Do not search ordinary tool results for an arbitrary "{", to avoid mistaking argument fragments for screen JSON.
        val jsonPart = raw.trimStart()
        if (!jsonPart.startsWith("{")) return null
        // Take only up to the first complete JSON object at the end, so later diagnostic text is not mixed into the state summary.
        val jsonOnly = jsonPart.substringBefore("\n\n")
        val root = runCatching { Json.parseToJsonElement(jsonOnly.trimStart()).jsonObject }
            .getOrNull() ?: return null
        val textBlocks = root["textBlocks"]?.jsonArray
            ?.mapNotNull { node -> node.jsonObject["text"]?.jsonPrimitive?.content }
            .orEmpty()
        val targets = root["targets"]?.jsonArray
            ?.mapNotNull { node -> node.jsonObject["text"]?.jsonPrimitive?.content }
            .orEmpty()
        return (textBlocks + targets)
            .mapNotNull { cleanVisibleLine(it, preserveCoordinates = false) }
            .distinct()
    }

    private fun cleanVisibleLine(line: String, preserveCoordinates: Boolean): String? {
        val trimmed = line.trim()
            .removePrefix("-")
            .trim()
        if (trimmed.isBlank()) return null

        val quoted = Regex("""「(.{2,160}?)」""").find(trimmed)?.groupValues?.getOrNull(1)
        val coordinateSuffix = if (preserveCoordinates) {
            Regex("""\s*@\(-?\d+,-?\d+\)""").find(trimmed)?.value.orEmpty()
        } else {
            ""
        }
        val targetIdRegex = Regex("""\[(text_\d+|t\d+|\d+)]\s*""")
        val candidate = quoted ?: run {
            val withoutCoords = trimmed.replace(Regex("""\s*@\(-?\d+,-?\d+\)"""), "")
            if (!preserveCoordinates) {
                withoutCoords.replace(targetIdRegex, "")
            } else {
                withoutCoords
            }
        }.trim().trim('"', '\'', '：', ':')

        return candidate
            .replace(Regex("""\s+"""), " ")
            .plus(coordinateSuffix)
            .takeIf { it.length in 2..180 }
    }

    private const val PURE_VISION_TRANSPORT_MARKER = "[PURE_VLM_SCREEN]"
}
