// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.animation.LinearInterpolator

/**
 * View viền sáng chảy mộng ảo của panel mirror không gian ảo (phong cách Siri / trợ lý Xiaobu).
 *
 * - Nét viền bo góc bám sát cạnh vật lý của màn hình, thích ứng động với bán kính góc R vật lý của màn hình thiết bị chủ;
 * - Hai lớp sáng mềm BlurMaskFilter chảy ngược chiều với tốc độ khác nhau, các gradient lệch tông màu loang vào nhau, tạo ánh sáng không gian liên tục biến đổi;
 * - Lớp trên cùng là một viền sáng mảnh phác đường nét, tổng thể độ trong suốt thấp và chồng thêm nhịp thở chậm;
 * - Không xử lý chạm, sự kiện xuyên xuống khung hình mirror bên dưới.
 */
class AuroraEdgeView(context: Context) : View(context) {
    private var lastDrawAtMs = 0L
    private val dp = context.resources.displayMetrics.density

    private val painter = com.droidance.core.device.agent.device.AuroraEdgePainter(dp)

    var cornerRadii: DeviceCornerRadii = DeviceCornerRadius.resolve(context)
        private set

    private var phase = 0f

    private companion object {
        /** Hiệu ứng sáng trang trí khoảng 30fps, tránh vẽ lại toàn màn hình vô ích trên màn hình chính 120Hz. */
        const val ANIMATION_FRAME_INTERVAL_MS = com.droidance.core.device.agent.device.AuroraEdgePainter.FRAME_INTERVAL_MS
    }

    // Trong một chu kỳ vòng chính quay hai vòng, vòng phụ quay ngược một vòng, nhịp thở nguyên lần, khởi động lại không bị nhảy
    private val flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = com.droidance.core.device.agent.device.AuroraEdgePainter.CYCLE_MS
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastDrawAtMs >= ANIMATION_FRAME_INTERVAL_MS) {
                lastDrawAtMs = now
                invalidate()
            }
        }
    }

    init {
        alpha = 0f
    }

    /** Cập nhật Insets màn hình thiết bị và thích ứng động bán kính R của bốn góc */
    fun updateWindowInsets(insets: WindowInsets?) {
        val resolved = DeviceCornerRadius.resolve(context, insets)
        if (resolved != cornerRadii) {
            cornerRadii = resolved
            updatePath()
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateWindowInsets(rootWindowInsets)
        } else {
            updateWindowInsets(null)
        }
        flowAnimator.start()
        animate().alpha(1f).setDuration(com.droidance.core.device.agent.device.AuroraEdgePainter.FADE_IN_MS).start()
    }

    override fun onDetachedFromWindow() {
        flowAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        updateWindowInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && rootWindowInsets != null) {
            cornerRadii = DeviceCornerRadius.resolve(context, rootWindowInsets)
        }
        updatePath()
    }

    private fun updatePath() {
        if (width <= 0 || height <= 0) return
        painter.resize(width, height, cornerRadii.toPathRadii())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter.draw(canvas, phase, 1f)
    }
}
