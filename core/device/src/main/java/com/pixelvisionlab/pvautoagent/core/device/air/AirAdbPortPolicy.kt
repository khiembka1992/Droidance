// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.air

object AirAdbPortPolicy {
    private const val LEGACY_ADB_TCPIP_PORT = 5555

    // lastPort is a fast candidate while the same wireless-debugging TLS service is still alive; after WiFi disconnects or switches, Android may
    // have shut down the old service, so it is only used for a quick attempt, and on failure we still must rely on the current port from Settings/mDNS.
    fun connectPorts(lastPort: Int?, settingsPort: Int?): List<Int> = buildList {
        addIfValid(lastPort)
        addIfValid(settingsPort)
    }.distinct()

    private fun MutableList<Int>.addIfValid(port: Int?) {
        if (port != null && port in 1..65535 && port != LEGACY_ADB_TCPIP_PORT) add(port)
    }
}
