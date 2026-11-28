// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.overlay

import android.content.Context
import kotlin.math.roundToInt

/**
 * Độ lệch độ cao người dùng kéo lớp phủ runtime tới, được giữ qua các tác vụ và lần khởi động lại App — thứ che khuất tầm nhìn thường là phần tử cố định của một App,
 * lần sau vẫn che, nên vị trí đáng để ghi nhớ.
 *
 * Runtime island ở màn hình chính và island điều khiển ở trang xem không gian ảo ghi chung một bản: điểm đáp mặc định của cả hai đều tính từ đáy màn hình, dời một chỗ là dời
 * cùng một island, quay lại chỗ kia nó vẫn ở đúng độ cao đó.
 */
object RuntimeIslandPosition {
    const val KEY_BOTTOM_OFFSET = "island_bottom_offset_px"
    const val KEY_OFFSET_X = "island_offset_x_px"
    const val KEY_OFFSET_Y = "island_offset_y_px"
    private const val KEY_COLLAPSED = "island_collapsed"

    private const val PREFS = "droidance_runtime_island_pos"

    fun defaultTopPx(screenHeightPx: Int, islandHeightPx: Int): Int =
        (screenHeightPx * (1f - RuntimeIslandStyle.BOTTOM_POSITION_FRACTION) - islandHeightPx / 2f)
            .roundToInt().coerceIn(0, (screenHeightPx - islandHeightPx).coerceAtLeast(0))

    fun read(context: Context): Int = prefs(context).getInt(KEY_OFFSET_Y, prefs(context).getInt(KEY_BOTTOM_OFFSET, 0))

    fun readX(context: Context): Int = prefs(context).getInt(KEY_OFFSET_X, 0)

    fun isCollapsed(context: Context): Boolean = prefs(context).getBoolean(KEY_COLLAPSED, false)

    fun writeCollapsed(context: Context, collapsed: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLAPSED, collapsed).apply()
    }

    fun write(context: Context, offsetPx: Int) {
        prefs(context).edit()
            .putInt(KEY_BOTTOM_OFFSET, offsetPx)
            .putInt(KEY_OFFSET_Y, offsetPx)
            .apply()
    }

    fun write(context: Context, offsetX: Int, offsetY: Int) {
        prefs(context).edit()
            .putInt(KEY_OFFSET_X, offsetX)
            .putInt(KEY_BOTTOM_OFFSET, offsetY)
            .putInt(KEY_OFFSET_Y, offsetY)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
