/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver

import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import java.util.function.Supplier

/**
 * The properties of a [FakeAdbServer] instance, that can be re-used to create
 * a similar server instance when needed. See [FakeAdbServer.getCurrentConfig] and
 * [FakeAdbServer.Builder.setConfig]
 */
class FakeAdbServerConfig {

    val hostHandlers = HashMap<String, Supplier<HostCommandHandler>>()

    val deviceHandlers = ArrayList<DeviceCommandHandler>()

    val devices = ArrayList<DeviceStateConfig>()

    val mdnsServices = ArrayList<MdnsService>()
}

/**
 * The properties of a [DeviceState] that can be re-used across [FakeAdbServer] instances.
 */
class DeviceStateConfig(
    val serialNumber: String,
    val files: ArrayList<DeviceFileState>,
    val logcatMessages: ArrayList<String>,
    val clients: ArrayList<ClientState>,
    val hostConnectionType: HostConnectionType,
    val manufacturer: String,
    val model: String,
    val buildVersionRelease: String,
    val buildVersionSdk: String,
    val cpuAbi: String,
    var deviceStatus: DeviceState.DeviceStatus,
)
