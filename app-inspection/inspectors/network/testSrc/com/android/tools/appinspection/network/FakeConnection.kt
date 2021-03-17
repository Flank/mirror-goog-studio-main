/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.network

import androidx.inspection.Connection
import studio.network.inspection.NetworkInspectorProtocol

class FakeConnection : Connection() {

    val httpData = mutableListOf<NetworkInspectorProtocol.HttpConnectionEvent>()
    val speedData = mutableListOf<NetworkInspectorProtocol.SpeedEvent>()

    override fun sendEvent(data: ByteArray) {
        val event = NetworkInspectorProtocol.Event.parseFrom(data)
        if (event.hasSpeedEvent()) {
            speedData.add(event.speedEvent)
        } else if (event.hasHttpConnectionEvent()) {
            httpData.add(event.httpConnectionEvent)
        }
    }

    fun findHttpEvent(
        eventType: NetworkInspectorProtocol.HttpConnectionEvent.UnionCase,
    ): NetworkInspectorProtocol.HttpConnectionEvent? {
        return httpData.firstOrNull { it.unionCase == eventType }
    }
}
