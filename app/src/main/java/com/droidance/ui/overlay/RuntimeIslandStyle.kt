// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Token thị giác runtime island. Đảo runtime màn hình chính (bitmap tổng hợp từ sidecar), đảo điều khiển mirror tiếp quản, đảo điều khiển trang xem không gian ảo dùng chung
 * một bộ màu và bo góc, ba nơi không còn viết lại mã hex riêng.
 *
 * Ngôn ngữ hình ảnh tiết chế kiểu runtime island iOS: viên nang tối liền khối + chữ trắng, nhân vật hòa thẳng vào thân đảo, nút thao tác dùng chung đế tròn trung tính cùng kích thước.
 * Nút tạm dừng/tiếp quản và nút dừng giữ nền trung tính bán trong suốt và biểu tượng trắng thuần thống nhất, không ngả vàng theo trạng thái tiếp quản.
 */
object RuntimeIslandStyle {
    const val PILL_FILL = "#0D0E10"
    const val HAIRLINE = "#343840"
    const val LABEL = "#FFFFFF"
    const val LABEL_SECONDARY = "#8E8E93"
    const val PAUSED_TINT = "#FFFFFF"
    const val STOP_TINT = "#FFFFFF"
    const val NEUTRAL_CHIP_FILL = "#1FFFFFFF"
    const val PAUSED_CHIP_FILL = "#1FFFFFFF"

    // ── Hình học của đảo: màn hình chính và không gian ảo dùng chung một bộ, không co giãn ở mọi trạng thái ──────────────
    // Scale the whole composition together, including text, mascot, controls and their glyphs.
    const val SCALE = 1.25f
    const val WIDTH_DP = 172f * SCALE
    const val HEIGHT_DP = 32f * SCALE
    // The expanded viewer reserves room for Back and non-overlapping touch targets.
    const val VIEWER_WIDTH_DP = 268f
    const val VIEWER_HEIGHT_DP = 48f
    const val VIEWER_CONTROL_TARGET_DP = 44f
    const val CHIP_SIZE_DP = 20f * SCALE
    const val STOP_GLYPH_SIZE_DP = 8f * SCALE
    const val STOP_GLYPH_RADIUS_DP = 2f * SCALE
    const val CHIP_GAP_DP = 4f * SCALE
    const val EDGE_PADDING_DP = 6f * SCALE
    const val SPACE_TOGGLE_WIDTH_DP = 8f * SCALE
    const val SPACE_TOGGLE_ARROW_OFFSET_DP = 12f * SCALE
    const val MASCOT_LEFT_DP = 8f * SCALE
    const val MASCOT_SIZE_DP = 20f * SCALE
    const val MASCOT_LABEL_GAP_DP = 4f * SCALE
    const val LABEL_INSET_DP = MASCOT_LEFT_DP + MASCOT_SIZE_DP + MASCOT_LABEL_GAP_DP
    const val LABEL_TEXT_SP = 11f * SCALE
    /** Trên màn hình hẹp phải chừa ít nhất ngần này mỗi bên, nếu không đảo sẽ dính sát mép màn hình. */
    const val SIDE_MIN_MARGIN_DP = 16f
    /** The default island center sits one fifth of the screen height above the bottom. */
    const val BOTTOM_POSITION_FRACTION = .2f

    /** Same small stop glyph in the bitmap island and the native viewer; hit target is separate. */
    fun stopIcon(density: Float): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(STOP_TINT))
        cornerRadius = STOP_GLYPH_RADIUS_DP * density
        val size = (STOP_GLYPH_SIZE_DP * density).toInt()
        setSize(size, size)
    }

    /** Each rendering owner retains its paths, avoiding shared mutable drawing state. */
    class SpaceTogglePainter {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(LABEL_SECONDARY)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val arrows = Path()
        private var previousDensity = Float.NaN
        private var previousCenterY = Float.NaN
        private var previousExpanded = false

        fun draw(canvas: Canvas, density: Float, centerY: Float, expanded: Boolean) {
            if (density != previousDensity || centerY != previousCenterY || expanded != previousExpanded) {
                previousDensity = density
                previousCenterY = centerY
                previousExpanded = expanded
                stroke.strokeWidth = 1.2f * SCALE * density
                arrows.rewind()
                val centerX = (MASCOT_LEFT_DP + (MASCOT_SIZE_DP + SPACE_TOGGLE_WIDTH_DP) / 2f) * density
                for (side in -1..1 step 2) {
                    val arrowX = centerX + side * SPACE_TOGGLE_ARROW_OFFSET_DP * density
                    val direction = side * if (expanded) -1f else 1f
                    val tipX = arrowX + direction * 1.6f * SCALE * density
                    val tailX = arrowX - direction * 1.6f * SCALE * density
                    arrows.moveTo(tailX, centerY - 2.5f * SCALE * density)
                    arrows.lineTo(tipX, centerY)
                    arrows.lineTo(tailX, centerY + 2.5f * SCALE * density)
                }
            }
            canvas.drawPath(arrows, stroke)
        }
    }

    fun fillColor(): Int = Color.parseColor(PILL_FILL)

    /** Nút trong đảo: viên nang trung tính bo tròn hoàn toàn. */
    fun chip(density: Float, fill: String, radiusDp: Float = 13f): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            cornerRadius = density * radiusDp
        }

    fun chipFill(paused: Boolean): String = if (paused) PAUSED_CHIP_FILL else NEUTRAL_CHIP_FILL

    fun chipTint(paused: Boolean): Int =
        Color.parseColor(if (paused) PAUSED_TINT else LABEL)
}
