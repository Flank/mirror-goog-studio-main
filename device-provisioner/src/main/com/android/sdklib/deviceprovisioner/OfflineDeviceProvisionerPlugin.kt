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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.deviceInfo
import com.android.adblib.scope
import com.android.adblib.serialNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Provides [DeviceHandle]s for devices that are connected to ADB but in a state other than ONLINE.
 * We cannot do anything with them, but we can alert the user to their presence, and in the
 * UNAUTHORIZED state, prompt them to accept connection on the device.
 */
class OfflineDeviceProvisionerPlugin : DeviceProvisionerPlugin {
  // High priority so that this claims offline devices before other plugins
  override val priority = 1000

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    when (device.deviceInfo.deviceState) {
      ONLINE -> return null
      else -> {
        val handle = OfflineDeviceHandle(device)
        devices.update { it + handle }

        // When the device goes online, remove this handle; it should be claimed by another plugin.
        device.scope.launch {
          device.deviceInfoFlow.takeWhile { it.deviceState != ONLINE }.collect()
          handle.stateFlow.update { Disconnected(it.properties) }
          devices.update { it - handle }
        }
        device.invokeOnDisconnection { devices.update { it - handle } }
        return handle
      }
    }
  }

  override val devices = MutableStateFlow(emptyList<DeviceHandle>())
}

private class OfflineDeviceHandle(device: ConnectedDevice) : DeviceHandle {
  override val stateFlow: MutableStateFlow<DeviceState> =
    MutableStateFlow(Connected(OfflineDeviceProperties(device.serialNumber), device))
}

class OfflineDeviceProperties(val serialNumber: String) :
  DeviceProperties by DeviceProperties.Builder().buildBase() {
  override fun title() = "Unknown device ($serialNumber)"
}
