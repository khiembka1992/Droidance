// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.device.air

object AirAdbPortPolicy {
    private const val LEGACY_ADB_TCPIP_PORT = 5555

    // lastPort là ứng viên nhanh khi cùng dịch vụ TLS gỡ lỗi không dây còn sống; sau khi WiFi ngắt hoặc chuyển mạng Android có thể
    // đã đóng dịch vụ cũ, nên nó chỉ dùng để thử nhanh, thất bại vẫn phải dựa vào cổng hiện tại từ Settings/mDNS.
    fun connectPorts(lastPort: Int?, settingsPort: Int?): List<Int> = buildList {
        addIfValid(lastPort)
        addIfValid(settingsPort)
    }.distinct()

    private fun MutableList<Int>.addIfValid(port: Int?) {
        if (port != null && port in 1..65535 && port != LEGACY_ADB_TCPIP_PORT) add(port)
    }
}
