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
import com.android.adblib.serialNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plugin providing access to physical devices, connected over USB or WiFi. */
class PhysicalDeviceProvisionerPlugin(val scope: CoroutineScope) : DeviceProvisionerPlugin {
  override val priority = 0

  private val devicesBySerial = hashMapOf<String, PhysicalDeviceHandle>()

  private val _devices = MutableStateFlow(emptyList<DeviceHandle>())
  override val devices = _devices.asStateFlow()

  override suspend fun claim(device: ConnectedDevice): Boolean {
    val properties = device.deviceProperties().allReadonly()

    val deviceProperties =
      PhysicalDeviceProperties.build {
        readCommonProperties(properties)
        connectionType =
          when (WIFI_SERIAL_NUMBER.matchEntire(device.serialNumber)) {
            null -> ConnectionType.USB
            else -> ConnectionType.WIFI
          }
      }

    val serialNumber = checkNotNull(properties["ro.serialno"]) { "Missing [ro.serialno] property" }

    // We want to be fairly confident this is a physical device; we expect USB-connected devices
    // to have their ADB serial number match their device serial number.
    val isUsb = deviceProperties.connectionType == ConnectionType.USB
    if (isUsb && serialNumber != device.serialNumber) {
      return false
    }

    val newState = Connected(deviceProperties, device)
    val handle =
      checkNotNull(
        devicesBySerial.compute(serialNumber) { _, handle ->
          when (handle) {
            null -> PhysicalDeviceHandle(MutableStateFlow(newState))
            else -> handle.also { it.stateFlow.value = newState }
          }
        }
      )

    updateDevices()

    // Update device state on termination. We keep it around in case it reconnects.
    device.invokeOnDisconnection { handle.stateFlow.value = Disconnected(handle.state.properties) }
    return true
  }

  private fun updateDevices() {
    _devices.value = devicesBySerial.values.toList()
  }
}

private class PhysicalDeviceHandle(override val stateFlow: MutableStateFlow<DeviceState>) :
  DeviceHandle

class PhysicalDeviceProperties(base: DeviceProperties, val connectionType: ConnectionType) :
  DeviceProperties by base {

  class Builder : DeviceProperties.Builder() {
    var connectionType: ConnectionType? = null
  }

  companion object {
    fun build(block: Builder.() -> Unit) =
      Builder().apply(block).run {
        PhysicalDeviceProperties(buildBase(), checkNotNull(connectionType))
      }
  }
}

enum class ConnectionType {
  USB,
  WIFI
}

private val WIFI_SERIAL_NUMBER = "adb-(.*)-.*\\._adb-tls-connect\\._tcp\\.?".toRegex()
