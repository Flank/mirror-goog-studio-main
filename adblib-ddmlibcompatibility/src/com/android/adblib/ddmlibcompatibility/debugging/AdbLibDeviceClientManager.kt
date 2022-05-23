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

import com.android.adblib.DeviceSelector
import com.android.adblib.createDeviceScope
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.clientmanager.DeviceClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job

internal class AdbLibDeviceClientManager(
  private val clientManager: AdbLibClientManager,
  private val bridge: AndroidDebugBridge,
  private val device: IDevice,
  private val listener: DeviceClientManagerListener
) : DeviceClientManager {

    private val deviceSelector = DeviceSelector.fromSerialNumber(device.serialNumber)

    /**
     * The [CoroutineScope] that is active as long as [deviceSelector] is connected.
     */
    val deviceScope = clientManager.session.createDeviceScope(deviceSelector)

    val deviceScopeJob: Job
        get() { return deviceScope.coroutineContext.job }

    override fun getDevice(): IDevice {
        return device
    }

    override fun getClients(): MutableList<Client> {
        //TODO: Not yet implemented
        return mutableListOf()
    }
}
