// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.overlay

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Parameter model for the physical corner radii (R corners) of the screen.
 *
 * The coordinate system matches Android's Path: clockwise in the order top-left, top-right, bottom-right, bottom-left.
 */
data class DeviceCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
) {
    /**
     * Builds the 8-element radius array used by [android.graphics.Path.addRoundRect]:
     * [tlX, tlY, trX, trY, brX, brY, blX, blY].
     */
    fun toPathRadii(): FloatArray = floatArrayOf(
        topLeft, topLeft,
        topRight, topRight,
        bottomRight, bottomRight,
        bottomLeft, bottomLeft
    )

    fun isAllZero(): Boolean =
        topLeft <= 0f && topRight <= 0f && bottomRight <= 0f && bottomLeft <= 0f

    companion object {
        fun uniform(radius: Float): DeviceCornerRadii =
            DeviceCornerRadii(radius, radius, radius, radius)

        val ZERO = uniform(0f)
    }
}

object DeviceCornerRadius {
    /** Default fallback corner radius (38dp), consistent with the existing visual spec */
    const val DEFAULT_FALLBACK_RADIUS_DP = 38f

    /**
     * Resolves the physical corner radii (R corners) of the current device's screen.
     *
     * Strategy:
     * 1. Prefer the official Android 12+ (API 31+) [WindowInsets.getRoundedCorner] to extract the per-corner pixel radius;
     * 2. On older versions, or when Insets carry no corner information, try reading the system-internal resources (rounded_corner_radius / top / bottom);
     * 3. If neither yields valid information, fall back to [fallbackRadiusPx] (38dp by default).
     *
     * If the device is explicitly a square-corner screen (API 31+ provides Insets and every corner radius is 0), this faithfully returns 0f square corners.
     */
    fun resolve(
        context: Context,
        insets: WindowInsets? = null,
        fallbackRadiusPx: Float = context.resources.displayMetrics.density * DEFAULT_FALLBACK_RADIUS_DP
    ): DeviceCornerRadii {
        val windowInsets = insets ?: resolveWindowInsetsFromContext(context)

        // 1. Official Android 12+ (API 31+) WindowInsets.getRoundedCorner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowInsets != null) {
            val tl = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            val tr = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
            val br = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
            val bl = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)

            if (tl != null || tr != null || br != null || bl != null) {
                return DeviceCornerRadii(
                    topLeft = tl?.radius?.toFloat() ?: 0f,
                    topRight = tr?.radius?.toFloat() ?: 0f,
                    bottomRight = br?.radius?.toFloat() ?: 0f,
                    bottomLeft = bl?.radius?.toFloat() ?: 0f
                )
            }
        }

        // 2. Try reading the system-internal dimen resources
        val topDimen = getSystemDimenPx(context, "rounded_corner_radius_top")
        val bottomDimen = getSystemDimenPx(context, "rounded_corner_radius_bottom")
        val generalDimen = getSystemDimenPx(context, "rounded_corner_radius")

        if (topDimen != null || bottomDimen != null || generalDimen != null) {
            val top = (topDimen ?: generalDimen ?: 0f)
            val bottom = (bottomDimen ?: generalDimen ?: 0f)
            if (top > 0f || bottom > 0f) {
                return DeviceCornerRadii(
                    topLeft = top,
                    topRight = top,
                    bottomRight = bottom,
                    bottomLeft = bottom
                )
            }
        }

        // 3. Fallback
        return DeviceCornerRadii.uniform(fallbackRadiusPx)
    }

    private fun resolveWindowInsetsFromContext(context: Context): WindowInsets? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val wm = context.getSystemService(WindowManager::class.java)
            wm?.currentWindowMetrics?.windowInsets ?: wm?.maximumWindowMetrics?.windowInsets
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSystemDimenPx(context: Context, resName: String): Float? {
        val resId = context.resources.getIdentifier(resName, "dimen", "android")
        return if (resId > 0) {
            try {
                context.resources.getDimensionPixelSize(resId).toFloat()
            } catch (_: Throwable) {
                null
            }
        } else null
    }
}
