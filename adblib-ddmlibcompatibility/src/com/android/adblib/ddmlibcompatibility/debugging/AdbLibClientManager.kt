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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.clientmanager.ClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener

/**
 * Implementation of ddmlib's [ClientManager] using [AdbSession] services.
 */
internal class AdbLibClientManager(val session: AdbSession) : ClientManager {

    private val logger = thisLogger(session)

    override fun createDeviceClientManager(
        bridge: AndroidDebugBridge,
        device: IDevice,
        listener: DeviceClientManagerListener
    ): AdbLibDeviceClientManager {
        logger.debug { "Creating device client manager for device $device" }
        return AdbLibDeviceClientManager(this, bridge, device, listener).apply {
            startDeviceTracking()
        }
    }
}

