// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.agent

import com.droidance.core.agent.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ObservationTextExtractor {
    fun extract(raw: String, preserveCoordinates: Boolean = false): List<String> {
        if (raw.trimStart().startsWith(PURE_VISION_TRANSPORT_MARKER)) {
            return emptyList()
        }
        // observe_screen giờ lấy khối văn bản đọc được <untrusted_screen_content> làm chính (chế độ một view của CUA),
        // ưu tiên trích xuất từ đó; định dạng cũ (JSON trần) đi qua structuredTexts làm dự phòng, các dòng văn bản thuần lại dự phòng tiếp.
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
            .filterNot { it.trim().startsWith("[") }  // bỏ qua các tiêu đề mô tả như [văn bản màn hình…]/[AIR…]
            .mapNotNull { cleanVisibleLine(it, preserveCoordinates) }
            .distinct()
            .toList()
    }

    private fun structuredTexts(raw: String): List<String>? {
        // readableContent đã xử lý định dạng mới có khối văn bản; ở đây chỉ chấp nhận định dạng cũ thật sự bắt đầu bằng đối tượng JSON.
        // Không tìm kiếm chuỗi "{" tùy tiện trong kết quả công cụ thông thường, tránh nhầm đoạn tham số thành JSON màn hình.
        val jsonPart = raw.trimStart()
        if (!jsonPart.startsWith("{")) return null
        // Chỉ lấy đến đối tượng JSON hoàn chỉnh đầu tiên, tránh để văn bản chẩn đoán phía sau lẫn vào bản tóm tắt trạng thái.
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
