// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.agent.device

import com.droidance.core.agent.device.*
import com.droidance.core.knowledge.agent.device.*
import com.droidance.core.device.agent.device.*

import com.droidance.agent.device.*

import android.content.ComponentName
import android.content.Context
import com.droidance.agent.RuntimeCapabilityRegistry
import com.droidance.debug.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VirtualSpaceReadiness @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityRegistry: RuntimeCapabilityRegistry,
    private val virtualSpaceManager: VirtualSpaceManager,
    private val sidecarBridge: VirtualSpaceSidecarBridge,
    private val virtualSpaceRuntimePolicy: VirtualSpaceRuntimePolicy,
    private val debugLogManager: DebugLogManager,
    private val driver: NativeInputDriverClient,
    private val client: dagger.Lazy<com.droidance.runtime.RuntimeProcessClient>
) {
    private companion object {
        const val INPUT_PROBE_ATTEMPTS = 3
        const val INPUT_PROBE_RETRY_MS = 200L
    }

    fun ensureReadyResult(requireHealthProbe: Boolean = false): VirtualSpaceReadinessResult {
        if (!client.get().isOwner) {
            val result = client.get().command("prepareDisplay", android.os.Bundle().apply { putBoolean("probe", requireHealthProbe) })
            val id = result.getInt("display", -1).takeIf { it > 0 }
            if (id != null) virtualSpaceManager.adoptRuntimeDisplay(id, result.getInt("width"), result.getInt("height"),
                result.getInt("density"), null, null, false)
            return VirtualSpaceReadinessResult(VirtualSpaceReadinessStatus.valueOf(result.getString("status")!!), id,
                result.getString("message").orEmpty())
        }
        val mode = capabilityRegistry.currentStrategy().selection.effectiveMode
            ?: return failed(
                status = VirtualSpaceReadinessStatus.DISPLAY_MISSING,
                displayId = null,
                message = "Virtual space requires a selected runtime mode."
            )
        val existing = capabilityRegistry.getVirtualDisplayId()
        if (existing != null && virtualSpaceManager.hasLiveSessionForMode(mode)) {
            // Chỉ chạy thăm dò sức khỏe khi được yêu cầu rõ ràng (tạo mới/tự kiểm tra ở trang cài đặt) — nó sẽ khởi động Activity thăm dò trên màn hình ảo.
            // Mỗi lượt hành động đều gọi phương thức này; nếu ở đây chạy lại thăm dò vì cờ sức khỏe chưa ready, nó sẽ khiến App đích đang mở
            // bị che lặp lại bằng trang thăm dò Droidance (trong observe pkg=com.droidance.debug), dẫn đến "đã mở ứng dụng mà không thao tác được".
            // Khi display đã tồn tại và active thì dùng lại trực tiếp, kiểm tra sức khỏe chỉ làm một lần lúc tạo.
            if (requireHealthProbe || capabilityRegistry.refresh().virtualSpaceHealthReason
                    .startsWith("[INPUT_PERMISSION_DENIED]")) {
                return runHealthProbe(existing, mode)
            }
            return VirtualSpaceReadinessResult(
                status = VirtualSpaceReadinessStatus.READY,
                displayId = existing,
                message = "Virtual space is ready on displayId=$existing."
            )
        }
        if (existing != null) {
            debugLogManager.appendEvent(
                "Virtual space stale sidecar session discarded before task entry: displayId=$existing mode=${mode.name}"
            )
            virtualSpaceManager.discardStaleSession(
                "stale sidecar session discarded before task entry"
            )
        }
        val started = virtualSpaceManager.start(mode)
        if (started != null) {
            val probe = runHealthProbe(started, mode)
            if (!probe.ready) return probe
            if (probe.status == VirtualSpaceReadinessStatus.FRAME_PENDING) return probe
            return VirtualSpaceReadinessResult(
                status = VirtualSpaceReadinessStatus.READY,
                displayId = started,
                message = "Virtual space started and verified on displayId=$started."
            )
        }
        val reason = virtualSpaceManager.lastFailureReason()
            .ifBlank { "Virtual space is selected, but no virtual displayId is available." }
        capabilityRegistry.markVirtualSpaceHealthFailed(reason)
        return VirtualSpaceReadinessResult(
            status = VirtualSpaceReadinessStatus.DISPLAY_MISSING,
            displayId = null,
            message = reason
        )
    }

    fun probeReadyResult(): VirtualSpaceReadinessResult = ensureReadyResult(requireHealthProbe = true)

    private fun runHealthProbe(displayId: Int, mode: RuntimeMode): VirtualSpaceReadinessResult {
        driver.command("probeReset")
        val launch = sidecarBridge.launchActivity(displayId, probeComponentName(), mode)
        if (!launch.ok) {
            return failed(
                status = VirtualSpaceReadinessStatus.LAUNCH_FAILED,
                displayId = displayId,
                message = "[VIRTUAL_SPACE_LAUNCH_FAILED] ${launch.detail.ifBlank { "probe launch failed" }}"
            )
        }
        val frameFailure = waitForNonBlackFrame(displayId, mode)
        val inputFailure = warmUpInputChannel(displayId, mode)
        if (inputFailure != null) {
            return failed(VirtualSpaceReadinessStatus.INPUT_PERMISSION_DENIED, displayId,
                "[INPUT_PERMISSION_DENIED] ${inputFailure.detail}")
        }
        // Khi frame đen/không có frame thì kèm theo điểm đáp thực tế của thăm dò: phân biệt hai nguyên nhân gốc "bị chuyển hướng về màn hình chính" và "màn hình ảo render ra frame đen"
        if (frameFailure != null) {
            val (status, reason) = frameFailure
            if (
                status == VirtualSpaceReadinessStatus.FRAME_FAILED &&
                driver.command("probeState").getInt("display", -1) == displayId
            ) {
                capabilityRegistry.markVirtualSpaceHealthReady(displayId)
                debugLogManager.appendEvent(
                    "virtual_space: probe reached the target display; first frame is pending displayId=$displayId"
                )
                driver.command("probeFinish")
                return VirtualSpaceReadinessResult(
                    status = VirtualSpaceReadinessStatus.FRAME_PENDING,
                    displayId = displayId,
                    message = "[VIRTUAL_SPACE_FRAME_PENDING] Probe reached displayId=$displayId; " +
                        "the first render is still pending and will be verified after target launch."
                )
            }
            return failed(
                status = status,
                displayId = displayId,
                message = "$reason; ${describeProbe(displayId)}"
            )
        }
        // A rendered frame establishes capture readiness. Explicit input denial was handled above;
        // transient warm-up failures while the input window registers remain nonfatal.
        capabilityRegistry.markVirtualSpaceHealthReady(displayId)
        // Hủy ngay Activity thăm dò sau khi tự kiểm tra xong, tránh màn hình trắng còn sót lại và che frame đầu của App đích.
        driver.command("probeFinish")
        return VirtualSpaceReadinessResult(
            status = VirtualSpaceReadinessStatus.READY,
            displayId = displayId,
            message = "Virtual space is ready on displayId=$displayId."
        )
    }

    /**
     * Retry transient input-channel startup failures; explicit permission denial cannot self-heal.
     */
    private fun warmUpInputChannel(displayId: Int, mode: RuntimeMode): VirtualSpaceSidecarBridge.CommandResult? {
        repeat(INPUT_PROBE_ATTEMPTS) { attempt ->
            val tap = sidecarBridge.injectNative(displayId, "tap 16 16", mode)
            if (tap.ok) return null
            if (tap.inputPermissionDenied) return tap
            if (attempt < INPUT_PROBE_ATTEMPTS - 1) Thread.sleep(INPUT_PROBE_RETRY_MS)
        }
        debugLogManager.appendEvent(
            "virtual_space: input warm-up was not confirmed; runtime injection may recover displayId=$displayId"
        )
        return null
    }

    /** Thăm dò frame thành công trả về null; thất bại trả về (trạng thái, nguyên nhân), bên gọi bổ sung chẩn đoán điểm đáp rồi gọi failed() thống nhất. */
    private fun waitForNonBlackFrame(displayId: Int, mode: RuntimeMode): Pair<VirtualSpaceReadinessStatus, String>? {
        val deadline = System.currentTimeMillis() + virtualSpaceRuntimePolicy.renderWaitTimeoutMs()
        var lastReason = "[VIRTUAL_SPACE_FRAME_FAILED] no probe frame returned for displayId=$displayId"
        while (System.currentTimeMillis() < deadline) {
            val bytes = sidecarBridge.captureLatestPng(displayId, mode = mode)
            if (bytes != null) {
                val blackFrame = virtualSpaceRuntimePolicy.checkBlackFrame(bytes)
                if (!blackFrame.isBlack) return null
                lastReason = virtualSpaceRuntimePolicy.emptyFrameFailure()
            }
            Thread.sleep(virtualSpaceRuntimePolicy.renderWaitPollMs())
        }
        val status = if (lastReason.startsWith(VirtualSpaceRuntimePolicy.EMPTY_FRAME_MARKER)) {
            VirtualSpaceReadinessStatus.BLACK_FRAME
        } else {
            VirtualSpaceReadinessStatus.FRAME_FAILED
        }
        return status to lastReason
    }

    private fun failed(
        status: VirtualSpaceReadinessStatus,
        displayId: Int?,
        message: String
    ): VirtualSpaceReadinessResult {
        capabilityRegistry.markVirtualSpaceHealthFailed(message)
        // Ghi nguyên nhân gốc xuống đĩa: trước đây thăm dò thất bại chỉ thoáng hiện ở trang cài đặt, gói debug không thấy được kẹt ở bước nào
        debugLogManager.appendEvent("virtual_space: readiness probe failed [$status] $message")
        // Đường dẫn thất bại cũng phải hủy thăm dò (chẩn đoán điểm đáp đã đọc trường tĩnh ở thượng nguồn), tránh nó còn sót thành màn hình trắng đến tác vụ sau
        driver.command("probeFinish")
        return VirtualSpaceReadinessResult(status = status, displayId = displayId, message = message)
    }

    private fun describeProbe(expected: Int): String {
        val landed = driver.command("probeState").getInt("display", -1)
        return "Probe display=$landed, expected=$expected"
    }

    private fun probeComponentName(): String =
        ComponentName(context.packageName, VirtualSpaceProbeActivity::class.java.name).flattenToString()
}

data class VirtualSpaceReadinessResult(
    val status: VirtualSpaceReadinessStatus,
    val displayId: Int?,
    val message: String
) {
    val ready: Boolean
        get() = (
            status == VirtualSpaceReadinessStatus.READY ||
                status == VirtualSpaceReadinessStatus.FRAME_PENDING
            ) && displayId != null
}

enum class VirtualSpaceReadinessStatus {
    READY,
    FRAME_PENDING,
    DISPLAY_MISSING,
    LAUNCH_FAILED,
    FRAME_FAILED,
    BLACK_FRAME,
    INPUT_FAILED,
    INPUT_PERMISSION_DENIED,
    TEXT_INPUT_FAILED
}
