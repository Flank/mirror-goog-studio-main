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
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.time.Duration
import org.junit.After
import org.junit.Test

class LocalEmulatorProvisionerPluginTest {

  val session = FakeAdbSession()
  val avdManager = FakeAvdManager()
  val plugin = LocalEmulatorProvisionerPlugin(session, avdManager, Duration.ofMillis(100))
  val provisioner = DeviceProvisioner.create(session, listOf(plugin))

  @After
  fun tearDown() {
    avdManager.close()
    session.close()
  }

  var avdIndex = 1
  fun makeAvdInfo(index: Int): AvdInfo {
    val basePath = Path.of("/tmp/fake_avds/$index")
    return AvdInfo(
      "fake_avd_$index",
      basePath.resolve("config.ini"),
      basePath,
      null,
      mapOf(
        AvdManager.AVD_INI_DEVICE_MANUFACTURER to MANUFACTURER,
        AvdManager.AVD_INI_DEVICE_NAME to MODEL,
        AvdManager.AVD_INI_ANDROID_API to API_LEVEL.apiString,
        AvdManager.AVD_INI_ABI_TYPE to ABI.toString(),
        AvdManager.AVD_INI_DISPLAY_NAME to "Fake Device $index",
      ),
      AvdInfo.AvdStatus.OK
    )
  }

  inner class FakeAvdManager : LocalEmulatorProvisionerPlugin.AvdManager {
    val avds = mutableListOf<AvdInfo>()

    val runningDevices = mutableSetOf<FakeEmulatorConsole>()

    override suspend fun rescanAvds(): List<AvdInfo> = avds

    override suspend fun createAvd(): Boolean {
      avds += makeAvdInfo(avdIndex++)
      return true
    }

    override suspend fun editAvd(avdInfo: AvdInfo): Boolean {
      avds.remove(avdInfo)
      createAvd()
      return true
    }

    override suspend fun startAvd(avdInfo: AvdInfo) {
      val device = FakeEmulatorConsole(avdInfo.name, avdInfo.dataFolderPath.toString())
      session.deviceServices.configureDeviceProperties(
        DeviceSelector.fromSerialNumber("emulator-${device.port}"),
        mapOf(
          "ro.serialno" to "EMULATOR31X3X7X0",
          DevicePropertyNames.RO_BUILD_VERSION_SDK to API_LEVEL.apiString,
          DevicePropertyNames.RO_BUILD_VERSION_RELEASE to RELEASE,
          DevicePropertyNames.RO_PRODUCT_MANUFACTURER to MANUFACTURER,
          DevicePropertyNames.RO_PRODUCT_MODEL to MODEL,
          DevicePropertyNames.RO_PRODUCT_CPU_ABI to ABI.toString()
        )
      )
      device.start()
      runningDevices += device
      updateDevices()
    }

    override suspend fun stopAvd(avdInfo: AvdInfo) {
      runningDevices.removeIf { it.avdPath == avdInfo.dataFolderPath.toString() }
      updateDevices()
    }

    fun close() {
      runningDevices.forEach(FakeEmulatorConsole::close)
    }
  }

  private fun updateDevices() {
    session.hostServices.devices =
      DeviceList(
        avdManager.runningDevices.map { DeviceInfo("emulator-${it.port}", DeviceState.ONLINE) },
        emptyList()
      )
  }

  @Test
  fun offlineDevices(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 2 }

    val devices = provisioner.devices.value
    assertThat(devices.map { it.state.properties.title() })
      .containsExactly("Fake Device 1", "Fake Device 2")
    checkProperties(devices[0].state.properties as LocalEmulatorProperties)
  }

  @Test
  fun startAndStopDevice(): Unit = runBlockingWithTimeout {
    avdManager.createAvd()

    yieldUntil { provisioner.devices.value.size == 1 }

    val handle = provisioner.devices.value[0]
    handle.activationAction?.activate()

    yieldUntil { handle.state.connectedDevice != null }

    assertThat(provisioner.devices.value.map { it.state.properties.title() })
      .containsExactly("Fake Device 1")
    val properties = provisioner.devices.value[0].state.properties as LocalEmulatorProperties
    checkProperties(properties)
    assertThat(properties.androidRelease).isEqualTo("11")

    handle.deactivationAction?.deactivate()
    // Simulate the remove since EmulatorConsole.kill doesn't do it
    avdManager.runningDevices.clear()
    updateDevices()

    yieldUntil { handle.state.connectedDevice == null }

    assertThat(handle.state).isInstanceOf(Disconnected::class.java)
    assertThat(provisioner.devices.value.map { it.state.properties.title() })
      .containsExactly("Fake Device 1")
  }

  private fun checkProperties(properties: LocalEmulatorProperties) {
    assertThat(properties.manufacturer).isEqualTo(MANUFACTURER)
    assertThat(properties.model).isEqualTo(MODEL)
    assertThat(properties.androidVersion).isEqualTo(API_LEVEL)
    assertThat(properties.abi).isEqualTo(ABI)
    assertThat(properties.avdName).startsWith("fake_avd_")
    assertThat(properties.displayName).startsWith("Fake Device")
  }

  companion object {
    const val MANUFACTURER = "Google"
    const val MODEL = "Pixel 6"
    val API_LEVEL = AndroidVersion(31)
    val ABI = Abi.ARM64_V8A
    const val RELEASE = "11"
  }
}
