/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.ddmlibcompatibility.testutils

import com.android.annotations.concurrency.GuardedBy
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.clientmanager.DeviceClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener

class TestDeviceClientManagerListener : DeviceClientManagerListener {

    /**
     * Events are added any time [DeviceClientManager] invokes this listener.
     */
    @GuardedBy("eventList")
    private val eventList = mutableListOf<Event>()

    /**
     * Returns a snapshot copy of the current list of [Event].
     */
    fun events(): List<Event> {
        return synchronized(eventList) {
            eventList.toList()
        }
    }

    /**
     * Process the current list of [Event] in a synchronized block.
     */
    fun <R> filterEvents(processor: (List<Event>) -> R): R {
        return synchronized(eventList) {
            processor(eventList)
        }
    }

    fun clearEvents() {
        synchronized(eventList) {
            eventList.clear()
        }
    }

    override fun processListUpdated(
        bridge: AndroidDebugBridge,
        deviceClientManager: DeviceClientManager
    ) {
        sendEvent(EventKind.PROCESS_LIST_UPDATED, bridge, deviceClientManager)
    }

    override fun processNameUpdated(
        bridge: AndroidDebugBridge,
        deviceClientManager: DeviceClientManager,
        client: Client
    ) {
        sendEvent(EventKind.PROCESS_NAME_UPDATED, bridge, deviceClientManager, client)
    }

    override fun processDebuggerStatusUpdated(
        bridge: AndroidDebugBridge,
        deviceClientManager: DeviceClientManager,
        client: Client
    ) {
        sendEvent(EventKind.PROCESS_DEBUGGER_STATUS_UPDATED, bridge, deviceClientManager)
    }

    private fun sendEvent(
        kind: EventKind,
        bridge: AndroidDebugBridge,
        deviceClientManager: DeviceClientManager,
        client: Client? = null
    ) {
        synchronized(eventList) {
            eventList.add(Event(kind, bridge, deviceClientManager, client))
        }
    }

    data class Event(
        val kind: EventKind,
        val bridge: AndroidDebugBridge,
        val deviceClientManager: DeviceClientManager,
        val client: Client? = null
    )

    enum class EventKind {
        PROCESS_LIST_UPDATED,
        PROCESS_NAME_UPDATED,
        PROCESS_DEBUGGER_STATUS_UPDATED
    }
}
