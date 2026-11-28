// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.agent.device

import com.droidance.core.agent.device.*
import com.droidance.core.knowledge.agent.device.*
import com.droidance.core.device.agent.device.*

import com.droidance.agent.device.*

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

/**
 * Ghi nhận điểm đáp thực tế của probe: dùng để phân biệt khi kiểm tra sống chết thất bại
 * "probe bị hệ thống chuyển hướng sang màn hình chính (display=0)" và "probe ở trên màn hình ảo nhưng kết xuất frame đen".
 */
object VirtualSpaceProbeLanding {
    @Volatile var displayId: Int? = null
    @Volatile var landedAtMs: Long = 0L
    @Volatile private var active: java.lang.ref.WeakReference<Activity>? = null

    fun register(activity: Activity) {
        active = java.lang.ref.WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (active?.get() === activity) active = null
    }

    /**
     * Sau khi tự kiểm tra sẵn sàng xong, readiness chủ động gọi để huỷ Activity probe. Probe chỉ dùng để xác minh màn hình ảo xuất được frame + inject được,
     * dùng xong phải đóng: nếu không màn hình trắng của nó sẽ nằm mãi trên màn hình ảo, bị chụp thành frame trắng thuần của chính Droidance.
     * Sau khi huỷ, app đích rơi vào một màn hình ảo sạch, ngay từ frame đầu đã là chính nó.
     */
    fun finishActive() {
        val activity = active?.get() ?: return
        active = null
        activity.runOnUiThread {
            if (!activity.isFinishing) activity.finish()
        }
    }

    fun describe(expectedDisplayId: Int): String {
        val landed = displayId ?: return "probe activity never reported a display (not launched or crashed)"
        val ageSec = (System.currentTimeMillis() - landedAtMs) / 1000
        return if (landed == expectedDisplayId) {
            "probe landed on expected display=$landed (${ageSec}s ago)"
        } else {
            "probe REDIRECTED to display=$landed, expected=$expectedDisplayId (${ageSec}s ago)"
        }
    }
}

class VirtualSpaceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VirtualSpaceProbeLanding.displayId =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                display?.displayId
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.displayId
            }
        VirtualSpaceProbeLanding.landedAtMs = System.currentTimeMillis()
        VirtualSpaceProbeLanding.register(this)
        setContentView(View(this).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = false
        })
    }

    override fun onDestroy() {
        VirtualSpaceProbeLanding.clear(this)
        super.onDestroy()
    }
}
