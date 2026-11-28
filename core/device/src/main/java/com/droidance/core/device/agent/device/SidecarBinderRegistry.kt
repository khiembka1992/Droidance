// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.core.device.agent.device

import com.droidance.core.agent.device.*

import com.droidance.core.device.agent.device.*

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

    // Chẩn đoán: số lần phía app nhận được binder qua broadcast, để phân biệt "sidecar không gửi broadcast" và "app không nhận được broadcast"
    @Volatile
    var receivedCount: Int = 0
        private set

    fun set(uid: Int, value: IBinder?) {
        require(uid == 2000) { "Only the paired shell sidecar can be registered" }
        if (value == null) {
            binders.remove(uid)
        } else {
            // Sidecar sẽ định kỳ gửi lại Binder của cùng một dịch vụ, BroadcastReceiver mỗi lần đều có thể
            // nhận được một đối tượng proxy mới. Việc ghi đè vô điều kiện sẽ khiến backendSessionId dao động giữa chừng tác vụ,
            // ràng buộc thực thi đã bị đóng băng sau đó bị đánh giá nhầm là "session changed". Chỉ đổi
            // thế hệ khi Binder cũ đã chết; rebroadcast của cùng tiến trình thường trú giữ tham chiếu proxy ổn định nhận được lần đầu.
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
