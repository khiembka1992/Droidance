// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.agent

import com.droidance.core.agent.*
import com.droidance.core.device.agent.*

import com.droidance.agent.*

import android.content.Context
import com.droidance.core.agent.device.DeviceCapabilities
import com.droidance.core.agent.device.DeviceExecutionStrategy
import com.droidance.core.agent.device.DeviceExecutionStrategyResolver
import com.droidance.core.agent.device.DeviceModeResolution
import com.droidance.core.agent.device.DeviceModeResolver
import com.droidance.core.agent.device.ExecutionSpace
import com.droidance.core.agent.device.RuntimeMode
import com.droidance.core.device.agent.device.VirtualSpaceSidecarBridge
import com.droidance.core.device.agent.device.DeviceConnectionMethod
import com.droidance.core.device.agent.device.RootConnectionManager
import com.droidance.core.device.air.AirModePairingManager
import com.droidance.config.RuleConfigManager
import com.droidance.debug.DebugLogManager
import com.droidance.core.model.llm.LLMRouter
import com.droidance.core.agent.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bảng đăng ký năng lực runtime.
 *
 * Nơi đây ghi lại sự thật trong mã về "hiện tại có thể làm gì", tránh nhét trạng thái năng lực thời gian thực ngược vào Markdown.
 */
@Singleton
class RuntimeCapabilityRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelClient: com.droidance.core.model.llm.ModelClient,
    private val airModePairingManager: AirModePairingManager,
    private val rootConnection: RootConnectionManager,
    private val shizuku: com.droidance.core.device.agent.device.ShizukuConnectionManager,
    private val channelNotifications: com.droidance.core.device.air.AirModeNotificationController,
    private val virtualSpaceSidecarBridge: VirtualSpaceSidecarBridge,
    private val ruleConfigManager: RuleConfigManager,
    private val generalSettings: com.droidance.settings.GeneralSettingsRepository,
    private val debugLogManager: DebugLogManager,
    @ApplicationScope private val appScope: CoroutineScope
) : com.droidance.core.agent.device.RuntimeModeProvider {
    override fun currentMode(): RuntimeMode? = currentStrategy().selection.effectiveMode
    data class Snapshot(
        val screenCaptureAvailable: Boolean,
        val inputInjectionReady: Boolean,
        val humanizedTouchAvailable: Boolean = false,
        val visionProviderAvailable: Boolean,
        val adbBridgeAvailable: Boolean = false,
        val virtualSpaceBridgeAvailable: Boolean = false,
        val virtualSpaceHealthReady: Boolean = false,
        val virtualSpaceHealthReason: String = "",
        val updatedAtMs: Long
    ) {
        fun toCapabilities(): DeviceCapabilities = DeviceCapabilities(
            adbBridgeAvailable = adbBridgeAvailable,
            inputInjectionReady = inputInjectionReady,
            visionProviderAvailable = visionProviderAvailable,
            // Khi binder sidecar thường trú còn sống vẫn có thể tạo quan sát văn bản (virtual frame capture), không nên bỏ sót route WiFi
            // không liên quan này — nếu không, cổng airReady dù đã mở adbBridgeAvailable vẫn bị chặn lại ở đây.
            textObservationAvailable = screenCaptureAvailable || adbBridgeAvailable || virtualSpaceBridgeAvailable,
            virtualSpaceBridgeAvailable = virtualSpaceBridgeAvailable,
        )

    }

    @Volatile
    private var snapshot = Snapshot(
        screenCaptureAvailable = false,
        inputInjectionReady = false,
        visionProviderAvailable = false,
        updatedAtMs = 0L
    )
    private val prefs = context.getSharedPreferences("droidance_settings", Context.MODE_PRIVATE)
    @Volatile private var virtualSpaceHealthDisplayId: Int? = null
    @Volatile private var virtualSpaceHealthReason: String = ""

    init {
        if (!com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner &&
            rootConnection.currentMethod == DeviceConnectionMethod.ROOT) {
            appScope.launch(Dispatchers.IO) {
                if (rootConnection.authorize()) ensureResidentChannel()
            }
        }
        if (!com.droidance.core.device.agent.device.RuntimeProcessRole.isOwner) appScope.launch(Dispatchers.IO) {
            shizuku.state.collect { state ->
                if (rootConnection.currentMethod == DeviceConnectionMethod.SHIZUKU && state.ready) ensureResidentChannel()
            }
        }
        appScope.launch(Dispatchers.IO) {
            com.droidance.core.device.agent.device.SidecarBinderRegistry.deaths.collect {
                debugLogManager.appendPersistentEvent("air-pairing", "resident sidecar Binder died; recovering the device connection")
                recoverResidentChannel()
            }
        }
        // Ghép nối/kết nối thành công là chủ động dựng sidecar thường trú đầy đủ năng lực duy nhất, không đợi thao tác thiết bị đầu tiên khởi động lười.
        // The virtual display keeps using its resident Binder after the live ADB connection ends.
        appScope.launch {
            airModePairingManager.state.collect { state ->
                if (state == AirModePairingManager.PairingState.CONNECTED) {
                    ensureResidentChannel()
                }
            }
        }
    }

    /** Start after application initialization; the first observation establishes a silent baseline. */
    fun monitorChannelAvailability(onReady: suspend () -> Unit) {
        appScope.launch(Dispatchers.IO) {
            val availability = RuntimeChannelAvailability()
            var previouslyReady = false
            while (true) {
                runCatching {
                    shizuku.refresh()
                    airModePairingManager.ensureConnectedIfPaired()
                    if (!virtualSpaceSidecarBridge.isResidentBinderAlive()) recoverResidentChannel()
                    val current = refresh(force = true)
                    val mode = requestedMode() ?: availability.observedMode ?: currentResolution(current).recommendedMode
                    val ready = when (mode) {
                        RuntimeMode.STANDARD -> current.adbBridgeAvailable || current.virtualSpaceBridgeAvailable
                        else -> false
                    }
                    val modeChanged = mode != availability.observedMode

                    if (!ready && mode == RuntimeMode.STANDARD && replaceStaleResidentSidecar()) {
                        refresh(force = true)
                    }
                    val lost = availability.update(mode, ready)
                    if (ready && !previouslyReady) onReady()
                    previouslyReady = ready
                    if (modeChanged || ready) {
                        channelNotifications.cancelRuntimeChannelLost()
                    } else if (lost) {
                        channelNotifications.showRuntimeChannelLost(rootConnection.currentMethod)
                    }
                }.onFailure { debugLogManager.appendEvent("channel availability check failed: ${it.javaClass.simpleName}") }
                delay(15_000)
            }
        }
    }

    /**
     * After an APK update the resident sidecar keeps the previous code source and is no longer a
     * usable channel. Without the AIR shell, it relaunches itself from the installed APK.
     */
    private fun replaceStaleResidentSidecar(): Boolean {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val replaced = runCatching {
            virtualSpaceSidecarBridge.replaceStaleResidentSidecar(
                metrics.widthPixels.coerceAtLeast(1),
                metrics.heightPixels.coerceAtLeast(1),
                metrics.densityDpi.coerceAtLeast(1)
            )
        }.getOrDefault(false)
        val diagnostic = virtualSpaceSidecarBridge.lastSidecarStartDiagnostic
        if (replaced || diagnostic.startsWith("stale sidecar")) {
            debugLogManager.appendEvent("stale resident sidecar replacement ok=$replaced diag=${diagnostic.take(160)}")
        }
        return replaced
    }

    private fun recoverResidentChannel() {
        if (rootConnection.currentMethod == DeviceConnectionMethod.SHIZUKU) {
            if (shizuku.refresh().ready) ensureResidentChannel()
            return
        }
        if (rootConnection.currentMethod == DeviceConnectionMethod.ROOT) {
            if (rootConnection.rootReady) ensureResidentChannel()
            return
        }
        if (!airModePairingManager.shouldMaintainResidentChannel) return
        airModePairingManager.probeConnection()
        airModePairingManager.ensureConnectedIfPaired()
        if (airModePairingManager.isShellReady) ensureResidentChannel()
    }

    /** Sidecar thường trú đầy đủ năng lực: ensureResidentChannel idempotent, kích hoạt lặp lại vẫn an toàn. */
    private fun ensureResidentChannel() {
        appScope.launch(Dispatchers.IO) {
            if (rootConnection.currentMethod == DeviceConnectionMethod.ROOT) {
                if (!rootConnection.rootReady) return@launch
            } else if (rootConnection.currentMethod == DeviceConnectionMethod.SHIZUKU) {
                if (!shizuku.refresh().ready) return@launch
            } else if (!airModePairingManager.shouldMaintainResidentChannel || !airModePairingManager.isShellReady) return@launch
            if (requestedMode()?.let { it != RuntimeMode.STANDARD } == true) return@launch
            val mode = RuntimeMode.STANDARD
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val ok = runCatching {
                virtualSpaceSidecarBridge.ensureResidentChannel(
                    metrics.widthPixels.coerceAtLeast(1),
                    metrics.heightPixels.coerceAtLeast(1),
                    metrics.densityDpi.coerceAtLeast(1),
                    mode
                )
            }.getOrDefault(false)
            // Nếu chỉ ghi vào logcat thì gói debug xuất từ thiết bị thật hoàn toàn không thấy dòng này — trước đây khi điều tra "tắt WiFi rồi còn dùng được không"
            // phải suy luận hoàn toàn từ mã, chính vì chỗ này không ghi vào debugLogManager. Chuyển sang ghi ở đây, lần xuất sau sẽ thấy ngay.
            debugLogManager.appendEvent(
                "🔗 resident sidecar on paired: mode=${mode.name} ok=$ok" +
                    if (!ok) " diag=${virtualSpaceSidecarBridge.lastSidecarStartDiagnostic.take(160)}" else ""
            )
        }
    }

    fun refresh(force: Boolean = false): Snapshot {
        val current = snapshot
        if (!force && System.currentTimeMillis() - current.updatedAtMs < 5_000) return current
        val virtualBridgeAvailable = virtualSpaceSidecarBridge.isShellBridgeAvailable()
        val virtualHealthReady = virtualSpaceHealthDisplayId != null &&
            virtualSpaceHealthReason.isBlank() &&
            virtualBridgeAvailable

        val refreshed = Snapshot(
            screenCaptureAvailable = virtualBridgeAvailable || (rootConnection.currentMethod == DeviceConnectionMethod.WIRELESS && airModePairingManager.isConnected),
            inputInjectionReady = virtualBridgeAvailable,
            humanizedTouchAvailable = false,
            visionProviderAvailable = modelClient.canSendVisionMessages(),
            adbBridgeAvailable = rootConnection.currentMethod == DeviceConnectionMethod.WIRELESS && airModePairingManager.isConnected,
            virtualSpaceBridgeAvailable = virtualBridgeAvailable,
            virtualSpaceHealthReady = virtualHealthReady,
            virtualSpaceHealthReason = virtualSpaceHealthReason,
            updatedAtMs = System.currentTimeMillis()
        )
        snapshot = refreshed
        // Quan sát độc lập hai tín hiệu transport (§5.5): ADB trực tuyến và Binder thường trú được ghi riêng. Khi ADB từ khả dụng chuyển sang không khả dụng mà Binder
        // vẫn còn sống thì chỉ ghi chuyển dịch transport, không kích hoạt chặn ghép nối lại; chỉ khi cả hai đều mất mới nhắc cần kết nối lại,
        // và nói rõ không thể khởi động lại tiến trình sidecar từ hư không, không nói dối rằng có thể tự phục hồi offline vĩnh viễn.
        if (current.updatedAtMs > 0 &&
            (current.adbBridgeAvailable != refreshed.adbBridgeAvailable ||
                current.virtualSpaceBridgeAvailable != refreshed.virtualSpaceBridgeAvailable)
        ) {
            debugLogManager.appendEvent(
                "transport_changed(adbAvailable=${refreshed.adbBridgeAvailable}, residentBinderAvailable=${refreshed.virtualSpaceBridgeAvailable})"
            )
            if (!refreshed.adbBridgeAvailable && !refreshed.virtualSpaceBridgeAvailable) {
                debugLogManager.appendEvent(
                    "transport_reconnect_needed: resident Binder is unavailable and no live ADB transport remains"
                )
            }
        }

        return refreshed
    }

    fun current(): Snapshot = refresh()

    /**
     * Task entry may race the asynchronous resident sidecar registration. Resolve the strategy
     * from fresh capability snapshots for a bounded window, then freeze the returned value for
     * the task. A device-free chat still returns immediately when no live transport can become
     * ready, so this wait is specific to a plausible device execution startup race.
     */
    suspend fun awaitTaskStrategy(space: ExecutionSpace = requestedSpace()): DeviceExecutionStrategy {
        var strategy = currentStrategy(refresh(force = true), space)
        if (!isRunnableForTask(strategy) && (strategy.selection.requestedMode ?: RuntimeMode.STANDARD) == RuntimeMode.STANDARD &&
            kotlinx.coroutines.withContext(Dispatchers.IO) { replaceStaleResidentSidecar() }) {
            strategy = currentStrategy(refresh(force = true), space)
        }
        if (isRunnableForTask(strategy) || !couldBecomeReady(strategy)) return strategy

        val waitMs = ruleConfigManager.harnessPolicy().runtimeThresholds.taskEntryCapabilityWaitMs
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + waitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            delay(remaining.coerceAtMost(TASK_ENTRY_CAPABILITY_POLL_MS))
            strategy = currentStrategy(refresh(force = true), space)
            if (isRunnableForTask(strategy)) {
                debugLogManager.appendEvent(
                    "task_entry_capability_ready waited_ms=${SystemClock.elapsedRealtime() - startedAt}"
                )
                return strategy
            }
        }
        strategy = currentStrategy(refresh(force = true), space)
        debugLogManager.appendEvent(
            "task_entry_capability_unavailable waited_ms=${SystemClock.elapsedRealtime() - startedAt} " +
                "reason=${strategy.spaceSelection.unavailableReason ?: strategy.selection.unavailableReason ?: "no runnable route"}"
        )
        return strategy
    }

    fun currentResolution(snapshot: Snapshot = current()): DeviceModeResolution {
        return DeviceModeResolver.resolve(snapshot.toCapabilities())
    }

    fun requestedMode(): RuntimeMode? = generalSettings.runtimeMode()

    fun requestedSpace(): ExecutionSpace = ExecutionSpace.VIRTUAL_SPACE

    fun selectedMode(snapshot: Snapshot = current()): RuntimeMode? {
        return currentStrategy(snapshot).selection.effectiveMode
    }

    fun currentStrategy(
        snapshot: Snapshot = current(),
        space: ExecutionSpace = requestedSpace()
    ): DeviceExecutionStrategy {
        return DeviceExecutionStrategyResolver.resolve(
            requestedMode = requestedMode(),
            requestedSpace = space,
            resolution = currentResolution(snapshot),
            capabilities = snapshot.toCapabilities()
        )
    }

    private fun isRunnableForTask(strategy: DeviceExecutionStrategy): Boolean =
        strategy.selection.effectiveMode != null &&
            strategy.selection.unavailableReason == null &&
            strategy.spaceSelection.unavailableReason == null

    private fun couldBecomeReady(strategy: DeviceExecutionStrategy): Boolean {
        return when (strategy.selection.requestedMode ?: RuntimeMode.STANDARD) {
            RuntimeMode.STANDARD -> (when (rootConnection.currentMethod) {
                DeviceConnectionMethod.ROOT -> rootConnection.rootReady
                DeviceConnectionMethod.SHIZUKU -> shizuku.state.value.ready
                DeviceConnectionMethod.WIRELESS -> airModePairingManager.isPreviouslyPaired
            }) ||
                snapshot.adbBridgeAvailable || snapshot.virtualSpaceBridgeAvailable
            else -> false
        }
    }

    fun markVirtualSpaceHealthReady(displayId: Int) {
        virtualSpaceHealthDisplayId = displayId
        virtualSpaceHealthReason = ""
        snapshot = snapshot.copy(
            virtualSpaceHealthReady = true,
            virtualSpaceHealthReason = "",
            updatedAtMs = 0L
        )
    }

    fun markVirtualSpaceHealthFailed(reason: String) {
        virtualSpaceHealthDisplayId = null
        virtualSpaceHealthReason = reason.take(240)
        snapshot = snapshot.copy(
            virtualSpaceHealthReady = false,
            virtualSpaceHealthReason = virtualSpaceHealthReason,
            updatedAtMs = 0L
        )
    }

    fun getVirtualDisplayId(): Int? {
        return prefs.getInt("virtual_display_id", -1).takeIf { it >= 0 }
    }

    fun setVirtualDisplayId(displayId: Int) {
        prefs.edit().putInt("virtual_display_id", displayId).apply()
    }

    fun clearVirtualDisplayId() {
        prefs.edit().remove("virtual_display_id").apply()
        markVirtualSpaceHealthFailed("")
    }

    companion object {
        private const val TASK_ENTRY_CAPABILITY_POLL_MS = 200L
    }
}
