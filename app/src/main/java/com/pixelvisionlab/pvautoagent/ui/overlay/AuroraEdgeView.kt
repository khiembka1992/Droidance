// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.animation.LinearInterpolator

/**
 * Dreamy flowing-light edge View for the virtual space mirror panel (Siri / Breeno style).
 *
 * - A rounded stroke hugging the physical edge of the screen, dynamically adapting to the physical corner radius of the host device's screen;
 * - Two layers of BlurMaskFilter soft light flow in opposite directions at different speeds, and hue-offset gradients bleed into each other to produce constantly changing ambient light;
 * - A thin bright edge on the topmost layer outlines the contour, with an overall low opacity plus a slow breathing effect;
 * - Touch is not handled; events pass through to the mirror image below.
 */
class AuroraEdgeView(context: Context) : View(context) {
    private var lastDrawAtMs = 0L
    private val dp = context.resources.displayMetrics.density

    private val painter = com.pixelvisionlab.pvautoagent.core.device.agent.device.AuroraEdgePainter(dp)

    var cornerRadii: DeviceCornerRadii = DeviceCornerRadius.resolve(context)
        private set

    private var phase = 0f

    private companion object {
        /** Decorative flowing light runs at about 30fps, avoiding pointless full-screen redraws on a 120Hz main screen. */
        const val ANIMATION_FRAME_INTERVAL_MS = com.pixelvisionlab.pvautoagent.core.device.agent.device.AuroraEdgePainter.FRAME_INTERVAL_MS
    }

    // Within one cycle the main ring turns twice, the secondary ring turns once in reverse, and breathing completes a whole number of times, so a restart does not jump
    private val flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = com.pixelvisionlab.pvautoagent.core.device.agent.device.AuroraEdgePainter.CYCLE_MS
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

    /** Updates the device screen Insets and dynamically adapts the corner radius at the four corners */
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
        animate().alpha(1f).setDuration(com.pixelvisionlab.pvautoagent.core.device.agent.device.AuroraEdgePainter.FADE_IN_MS).start()
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
