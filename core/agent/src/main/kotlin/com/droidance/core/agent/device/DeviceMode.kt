// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.agent.device

import com.droidance.core.agent.device.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class RuntimeMode(val displayName: String, val eventIdentifier: String) {
    // EventStore wire identifiers stay stable; product names are independent of stored evidence.
    @SerialName("AIR") STANDARD("Standard", "AIR"),
    // Historical event identifier only; no Ultra runtime is shipped.
    @SerialName("PRO") ULTRA("Ultra", "PRO")
}

@Serializable
enum class ExecutionSpace {
    // Historical event identifier only; new execution is virtual-space only.
    MAIN_SCREEN,
    VIRTUAL_SPACE
}

data class DeviceCapabilities(
    val adbBridgeAvailable: Boolean = false,
    val inputInjectionReady: Boolean = false,
    val visionProviderAvailable: Boolean = false,
    val textObservationAvailable: Boolean = true,
    val virtualSpaceBridgeAvailable: Boolean = false
) {

    // Sau khi kết nối ADB trực tiếp (adbBridgeAvailable) ngắt (ví dụ tắt WiFi), chỉ cần sidecar thường trú được thiết lập trong lúc ghép nối
    // binder (virtualSpaceBridgeAvailable) còn sống thì AIR vẫn phải chạy được — đường này dùng Binder IPC cục bộ không
    // qua ADB. Trước đây chỉ xem adbBridgeAvailable, khiến WiFi vừa ngắt là báo "không có kênh khả dụng" dù sidecar còn nguyên vẹn,
    // làm cơ chế kênh thường trú thành vô dụng.
    val standardReady: Boolean
        get() = (adbBridgeAvailable || virtualSpaceBridgeAvailable) && textObservationAvailable
}

enum class DeviceRequirement(val diagnostic: String) {
    WIRELESS_DEBUGGING("Wireless debugging is unavailable"),
    SCREEN_OBSERVATION("Screen observation is unavailable"),
}

enum class ExecutionSpaceUnavailableCause(val diagnostic: String) {
    STANDARD_SIDECAR("Standard virtual-space sidecar is unavailable"),
    NO_RUNTIME_MODE("No runtime mode is available")
}

data class DeviceModeResolution(
    val runnableModes: Set<RuntimeMode>,
    val recommendedMode: RuntimeMode?,
    val missingRequirements: Map<RuntimeMode, List<DeviceRequirement>>
)

data class ExecutionSpaceSelection(
    val requestedSpace: ExecutionSpace,
    val effectiveSpace: ExecutionSpace,
    val unavailableCause: ExecutionSpaceUnavailableCause? = null
) {
    val unavailableReason: String?
        get() = unavailableCause?.let { "[BACKGROUND_DISPLAY_UNAVAILABLE] ${it.diagnostic}" }
}
