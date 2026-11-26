// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*

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

    // After the live ADB connection (adbBridgeAvailable) drops (for example when WiFi is turned off), as long as the resident sidecar
    // binder (virtualSpaceBridgeAvailable) established during pairing is still alive, AIR should still be able to run: this route's local Binder IPC does not
    // go through ADB. Previously only adbBridgeAvailable was checked, so as soon as WiFi dropped, even a healthy sidecar was judged as "no usable channel",
    // which made the resident channel mechanism useless in practice.
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
