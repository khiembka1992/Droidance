// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.core.device.agent.device

import com.pixelvisionlab.pvautoagent.core.agent.device.*

import com.pixelvisionlab.pvautoagent.core.device.agent.device.*

import android.os.IBinder
import android.os.RemoteException
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the sidecar Binder that the shell-identity server pushes to the app
 * via [SidecarBinderReceiver]. The bridge reads it from here instead of from
 * ServiceManager.getService.
 */
object SidecarBinderRegistry {
    private val deathEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deaths: kotlinx.coroutines.flow.SharedFlow<Unit> = deathEvents
    private val binders = ConcurrentHashMap<Int, IBinder>()

    // Diagnostics: how many times the app side received a binder via broadcast, to distinguish "the sidecar did not send a broadcast" from "the app did not receive the broadcast"
    @Volatile
    var receivedCount: Int = 0
        private set

    fun set(uid: Int, value: IBinder?) {
        require(uid == 2000) { "Only the paired shell sidecar can be registered" }
        if (value == null) {
            binders.remove(uid)
        } else {
            // The sidecar periodically rebroadcasts the Binder of the same service, and the BroadcastReceiver may get
            // a new proxy object each time. Overwriting unconditionally would make backendSessionId flap in the middle of a task,
            // and the already-frozen execution binding would then be misjudged as "session changed". Switch only when the old Binder is dead;
            // a rebroadcast from the same resident process keeps the stable proxy reference received the first time.
            val current = binders[uid]
            if ((current == null || !current.isBinderAlive) && watchForDeath(uid, value)) {
                binders[uid] = value
            }
            receivedCount++
        }
    }

    /**
     * A proxy reports isBinderAlive until an obituary or a failed call arrives. Without this link,
     * an idle app would keep the dead Binder and ignore the restarted sidecar's Binder as a duplicate.
     */
    private fun watchForDeath(uid: Int, binder: IBinder): Boolean =
        try {
            binder.linkToDeath({ if (binders.remove(uid, binder)) deathEvents.tryEmit(Unit) }, 0)
            true
        } catch (_: RemoteException) {
            false
        }

    fun get(uid: Int): IBinder? {
        if (uid != 2000) return null
        val binder = binders[uid] ?: return null
        if (binder.isBinderAlive) return binder
        binders.remove(uid, binder)
        return null
    }

    fun isAlive(uid: Int): Boolean = get(uid) != null

    fun clear(value: IBinder) {
        binders.entries
            .filter { it.value == value }
            .forEach { binders.remove(it.key, value) }
    }
}
