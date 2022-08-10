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
import com.android.adblib.deviceProperties
import com.android.adblib.scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plugin which provides handles for devices when no other plugin claims them. This will offer a
 * handle for any device, but offers no operations on that device and does not have any memory of
 * devices.
 */
class DefaultProvisionerPlugin : DeviceProvisionerPlugin {
  override val priority: Int = Int.MIN_VALUE

  private val _devices = MutableStateFlow<List<DefaultDeviceHandle>>(emptyList())
  override val devices: StateFlow<List<DeviceHandle>> = _devices.asStateFlow()

  override suspend fun claim(device: ConnectedDevice): Boolean {
    val properties = device.deviceProperties().allReadonly()
    val deviceProperties =
      DeviceProperties.Builder().apply { readCommonProperties(properties) }.buildBase()
    val handle = DefaultDeviceHandle(Connected(deviceProperties, device))

    _devices.value += handle

    device.invokeOnDisconnection {
      handle.stateFlow.value = Disconnected(deviceProperties)
      _devices.value -= handle
    }

    return true
  }

  private class DefaultDeviceHandle(state: Connected) : DeviceHandle {
    override val stateFlow = MutableStateFlow<DeviceState>(state)
  }
}
