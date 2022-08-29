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
package com.android.sdklib.deviceprovisioner.testing

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.ActivationParams
import com.android.sdklib.deviceprovisioner.Connected
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.Disconnected
import com.android.sdklib.deviceprovisioner.invokeOnDisconnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [DeviceProvisionerPlugin] that allows creating [FakeDeviceHandle]s that can be activated and
 * deactivated.
 */
class FakeAdbDeviceProvisionerPlugin(
  private val fakeAdb: FakeAdbServerProvider,
  override val priority: Int = 1
) : DeviceProvisionerPlugin {

  /** Claims any device that has been registered with [addDevice] (based on serial number). */
  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    val handle = devices.value.find { it.serialNumber == device.serialNumber } ?: return null
    handle.stateFlow.update { Connected(it.properties, device) }
    device.invokeOnDisconnection { handle.stateFlow.update { Disconnected(it.properties) } }
    return handle
  }

  override val devices = MutableStateFlow(emptyList<FakeDeviceHandle>())

  /**
   * Creates a FakeDeviceHandle in the Disconnected state.
   *
   * Note that, in order to allow simulating devices becoming known and unknown to the plugin, the
   * returned device will not be initially known to the plugin. That is, it will not be reported in
   * [devices] and it will not be claimed if it is activated.
   *
   * To get the device in the [Connected] state, it needs to be made known to the plugin using
   * [addDevice], and activated using its [ActivationAction], which adds it to FakeAdb.
   */
  fun newDevice(
    serialNumber: String = nextSerial(),
    properties: DeviceProperties = DEFAULT_PROPERTIES
  ): FakeDeviceHandle {
    return FakeDeviceHandle(Disconnected(properties), serialNumber)
  }

  /** Creates a FakeDeviceHandle in the Disconnected state that is already known to the plugin. */
  fun addNewDevice(
    serialNumber: String = nextSerial(),
    properties: DeviceProperties = DEFAULT_PROPERTIES
  ): FakeDeviceHandle = newDevice(serialNumber, properties).also { addDevice(it) }

  /** Makes the device known to the plugin, in its current state. */
  fun addDevice(device: FakeDeviceHandle) {
    devices.update { it + device }
  }

  /** Makes the device unknown to the plugin; i.e. it will no longer be returned from [devices]. */
  fun removeDevice(device: FakeDeviceHandle) {
    devices.update { it - device }
  }

  private var serialNumber = 1
  fun nextSerial(): String = "fake-device-${serialNumber++}"

  companion object {
    val DEFAULT_PROPERTIES =
      DeviceProperties.build {
        manufacturer = "Google"
        model = "Pixel 6"
        androidVersion = AndroidVersion(31)
        androidRelease = "11"
      }
  }

  inner class FakeDeviceHandle(
    initialState: DeviceState,
    val serialNumber: String,
  ) : DeviceHandle {
    var fakeAdbDevice: com.android.fakeadbserver.DeviceState? = null
      get() =
        synchronized(this) {
          return field
        }
      private set(value) = synchronized(this) { field = value }

    override val stateFlow = MutableStateFlow(initialState)

    override val activationAction =
      object : ActivationAction {
        override val label = "Activate"
        override val isEnabled = MutableStateFlow(true)
        override suspend fun activate(params: ActivationParams) {
          val properties = state.properties
          fakeAdbDevice =
            fakeAdb.connectDevice(
                serialNumber,
                properties.manufacturer ?: "(Unknown manufacturer)",
                properties.model ?: "(Unknown model)",
                properties.androidRelease ?: "(Unknown release)",
                properties.androidVersion.toString(),
                com.android.fakeadbserver.DeviceState.HostConnectionType.USB
              )
              .also { it.deviceStatus = com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE }
        }
      }

    override val deactivationAction: DeactivationAction =
      object : DeactivationAction {
        override val label = "Activate"
        override val isEnabled = MutableStateFlow(true)
        override suspend fun deactivate() {
          fakeAdb.disconnectDevice(serialNumber)
          fakeAdbDevice = null
        }
      }
  }
}
