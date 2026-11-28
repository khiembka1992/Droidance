// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.overlay

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Mô hình tham số bán kính góc R (bán kính bo góc) bốn góc vật lý của màn hình.
 *
 * Hệ tọa độ giống Android Path: theo chiều kim đồng hồ lần lượt là trên-trái, trên-phải, dưới-phải, dưới-trái.
 */
data class DeviceCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
) {
    /**
     * Tạo mảng bán kính 8 phần tử dùng cho [android.graphics.Path.addRoundRect]:
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
    /** Bán kính bo góc dự phòng mặc định (38dp), giữ nhất quán với chuẩn thị giác ban đầu */
    const val DEFAULT_FALLBACK_RADIUS_DP = 38f

    /**
     * Phân giải bán kính góc R bốn góc vật lý màn hình của thiết bị hiện tại.
     *
     * Chiến lược:
     * 1. Ưu tiên dùng [WindowInsets.getRoundedCorner] chính thức của Android 12+ (API 31+) để lấy bán kính pixel từng góc;
     * 2. Với bản thấp hơn hoặc khi Insets không có thông tin bo góc, thử đọc tài nguyên nội bộ hệ thống (rounded_corner_radius / top / bottom);
     * 3. Nếu đều không lấy được thông tin hợp lệ, lùi về [fallbackRadiusPx] (mặc định 38dp).
     *
     * Nếu thiết bị rõ ràng là màn hình góc vuông (API 31+ cung cấp Insets và radius các góc bằng 0), sẽ trả về trung thực 0f góc vuông.
     */
    fun resolve(
        context: Context,
        insets: WindowInsets? = null,
        fallbackRadiusPx: Float = context.resources.displayMetrics.density * DEFAULT_FALLBACK_RADIUS_DP
    ): DeviceCornerRadii {
        val windowInsets = insets ?: resolveWindowInsetsFromContext(context)

        // 1. WindowInsets.getRoundedCorner chính thức của Android 12+ (API 31+)
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

        // 2. Thử đọc tài nguyên dimen nội bộ hệ thống
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

        // 3. Dự phòng
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
