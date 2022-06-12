/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.ddmlib

import com.android.ddmlib.internal.DeviceListMonitorTask
import com.android.ddmlib.internal.DeviceMonitor.DeviceListComparisonResult
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeviceMonitorTest {

    @get:Rule
    var adbRule =
        FakeAdbRule().withEmulatorConsoleFactory { name, path ->
            FakeEmulatorConsoleWithLatency(name, path)
        }

    @Test
    fun testDeviceListMonitor() {
        val map = DeviceListMonitorTask.parseDeviceListResponse(
            """
          R32C801BL5K	device
          0079864fd1d150fd	unauthorized
          002ee7a50f6642d3	sideload
      """.trimIndent()
        )
        assertThat(map["R32C801BL5K"]).isEqualTo(IDevice.DeviceState.ONLINE)
        assertThat(map["0079864fd1d150fd"]).isEqualTo(IDevice.DeviceState.UNAUTHORIZED)
        assertThat(map["002ee7a50f6642d3"]).isEqualTo(IDevice.DeviceState.SIDELOAD)
    }

    @Test
    fun testDeviceListComparator() {
        val previous = Arrays.asList(
            mockDevice("1", IDevice.DeviceState.ONLINE),
            mockDevice("2", IDevice.DeviceState.BOOTLOADER)
        )
        val current = Arrays.asList(
            mockDevice("2", IDevice.DeviceState.ONLINE),
            mockDevice("3", IDevice.DeviceState.OFFLINE)
        )
        val result = DeviceListComparisonResult.compare(previous, current)
        assertThat(result.updated.size).isEqualTo(1)
        assertThat(result.updated[previous[1]]).isEqualTo(IDevice.DeviceState.ONLINE)
        assertThat(result.removed.size).isEqualTo(1)
        assertThat(result.removed[0].serialNumber).isEqualTo("1")
        assertThat(result.added.size).isEqualTo(1)
        assertThat(result.added[0].serialNumber).isEqualTo("3")
    }

    @Test
    fun testDeviceUpdate() {
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        assertThat(device.avdName).isNull()
        assertThat(device.avdPath).isNull()
        assertThat(device.avdData.get()).isNull()
    }

    @Test
    fun testEmulatorUpdate() {
        adbRule.attachDevice("emulator-123", "Google", "Pixel", "29", "29", avdName = "MyAvd", avdPath = "/path")
        val device: IDevice = adbRule.bridge.devices.single()

        assertThat(device.avdName).isNull()
        assertThat(device.avdPath).isNull()
        try {
            device.avdData.get(1, TimeUnit.MILLISECONDS)
            error("Timeout Expected")
        }
        catch (ex: TimeoutException) {
            assertThat(ex.message).startsWith("Waited 1 milliseconds ")
        }

        val console = EmulatorConsole.getConsole(device) as FakeEmulatorConsoleWithLatency
        console.latch.countDown()

        assertThat(device.avdData.get()).isEqualTo(AvdData("MyAvd", "/path"))
        assertThat(device.avdName).isEqualTo("MyAvd")
        assertThat(device.avdPath).isEqualTo("/path")
    }

    private fun mockDevice(serial: String, state: IDevice.DeviceState): IDevice {
        val device: IDevice = mock()
        `when`(device.serialNumber).thenReturn(serial)
        `when`(device.state).thenReturn(state)
        return device
    }

    class FakeEmulatorConsoleWithLatency(
        private val actualAvdName: String? = null,
        private val actualAvdPath: String? = null
    ) : EmulatorConsole() {
        val latch = CountDownLatch(1)

        override fun getAvdName(): String? {
            latch.await()
            return actualAvdName
        }

        override fun getAvdPath(): String {
            latch.await()
            if (actualAvdPath != null) {
                return actualAvdPath
            }
            else {
                throw CommandFailedException()
            }
        }

        override fun close() {}

        override fun kill() {}

        override fun startEmulatorScreenRecording(args: String?): String {
            TODO("Not yet implemented")
        }

        override fun stopScreenRecording(): String {
            TODO("Not yet implemented")
        }
    }
}
