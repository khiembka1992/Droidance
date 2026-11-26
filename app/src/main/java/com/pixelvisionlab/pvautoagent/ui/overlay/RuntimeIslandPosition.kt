// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.overlay

import android.content.Context
import kotlin.math.roundToInt

/**
 * The height offset the user dragged the runtime overlay to, retained across tasks and App restarts — what blocks the view is usually some fixed
 * element of some App that will block again next time, so the position is worth remembering.
 *
 * The main-screen runtime island and the virtual space viewer page's control island share a single record: both default positions are measured from the bottom of the screen, so moving one is moving
 * the same island, and when you go back to the other place it is still at the same height.
 */
object RuntimeIslandPosition {
    const val KEY_BOTTOM_OFFSET = "island_bottom_offset_px"
    const val KEY_OFFSET_X = "island_offset_x_px"
    const val KEY_OFFSET_Y = "island_offset_y_px"
    private const val KEY_COLLAPSED = "island_collapsed"

    private const val PREFS = "pvautoagent_runtime_island_pos"

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
