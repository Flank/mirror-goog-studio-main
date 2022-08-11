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

import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Tests the [DeviceProvisioner] and its two basic plugins, [PhysicalDeviceProvisionerPlugin] and
 * [DefaultProvisionerPlugin]
 */
class DeviceProvisionerTest {
  val fakeSession = FakeAdbSession()

  val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope)
  val provisioner = DeviceProvisioner.create(fakeSession, listOf(plugin))

  object SerialNumbers {
    val physicalUsb = "X1058A"
    val physicalWifi = "adb-X1BQ704RX2B-VQ4ADB._adb-tls-connect._tcp."
    val emulator = "emulator-5554"
  }

  init {
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalUsb),
      mapOf(
        "ro.serialno" to SerialNumbers.physicalUsb,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6"
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalWifi),
      mapOf(
        "ro.serialno" to "X1BQ704RX2B",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6"
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.emulator),
      mapOf(
        "ro.serialno" to "EMULATOR31X3X7X0",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "sdk_goog3_x86_64"
      )
    )
  }

  private fun setDevices(vararg serialNumber: String) {
    fakeSession.hostServices.devices =
      DeviceList(serialNumber.map { DeviceInfo(it, DeviceState.ONLINE) }, emptyList())
  }

  @Test
  fun physicalUsbWiFiProperties() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalUsb, SerialNumbers.physicalWifi)

      // The plugin adds the devices one at a time, so there are two events here
      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(2)

        val handlesByType =
          handles.associateBy { (it.state.properties as PhysicalDeviceProperties).connectionType }

        assertThat(handlesByType).hasSize(2)
        assertThat(handlesByType[ConnectionType.USB]?.state?.connectedDevice?.serialNumber)
          .isEqualTo(SerialNumbers.physicalUsb)
        assertThat(handlesByType[ConnectionType.WIFI]?.state?.connectedDevice?.serialNumber)
          .isEqualTo(SerialNumbers.physicalWifi)
      }
    }
  }

  @Test
  fun physicalDeviceMaintainsIdentityOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalUsb)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(Connected::class.java)

          handle
        }

      // We also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.physicalUsb)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun defaultDeviceIsDistinctOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.emulator)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          // assertThat(handle).isInstanceOf(DefaultDeviceHandle::class.java)
          assertThat(handle.state).isInstanceOf(Connected::class.java)

          handle
        }

      // Now we also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      // We get two messages on the channel, one for the device becoming disconnected, and one
      // for the device list changing. We don't know what order they will occur in, but it
      // doesn't matter; just check the state after the second.
      channel.receiveUntilPassing { handles ->
        assertThat(handles).isEmpty()
        assertThat(originalHandle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.emulator)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }
}

/**
 * Receives messages on this channel until one is received that does not cause an [AssertionError]
 * in the supplied block. This should be used within a withTimeout() block. If timeout occurs, throws
 * a new AssertionError with the last received error as a cause, if one was received.
 */
suspend fun <T, R> Channel<T>.receiveUntilPassing(block: (T) -> R): R {
  var lastError: AssertionError? = null
  while (true) {
    try {
      return block(receive())
    } catch (e: AssertionError) {
      lastError = e
    } catch (e: CancellationException) {
      when (lastError) {
        null -> throw e
        else -> throw AssertionError("Expected message not received within timeout", lastError)
      }
    }
  }
}
