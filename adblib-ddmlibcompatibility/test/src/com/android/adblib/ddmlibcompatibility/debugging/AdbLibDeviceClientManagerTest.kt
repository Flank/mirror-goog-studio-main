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

import com.android.adblib.ddmlibcompatibility.testutils.TestDeviceClientManagerListener
import com.android.adblib.testing.FakeAdbLibSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import junit.framework.Assert
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class AdbLibDeviceClientManagerTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    val fakeAdb = FakeAdbRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun testScopeIsCancelledWhenDeviceDisconnects() = runBlocking {
        // Prepare
        val session = registerCloseable(FakeAdbLibSession())
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, _) = connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            ) as AdbLibDeviceClientManager

        // Act
        disconnectDevice(device.serialNumber)
        deviceClientManager.deviceScopeJob.join()

        // Assert
        Assert.assertFalse(deviceClientManager.deviceScope.isActive)
        Assert.assertEquals(0, listener.events.size)
    }

    private suspend fun connectTestDevice(): Pair<IDevice, DeviceState> {
        val deviceState = fakeAdb.attachDevice(
            "1234",
            "manuf",
            "model",
            "2022",
            "31",
            DeviceState.HostConnectionType.USB
        )

        // Ensure device is online
        deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return waitForOnlineDevice(deviceState)
    }

    private suspend fun waitForOnlineDevice(deviceState: DeviceState): Pair<IDevice, DeviceState> {
        while (true) {
            val device = fakeAdb.bridge.devices.find {
                it.isOnline && it.serialNumber == deviceState.deviceId
            }
            if (device != null) {
                return Pair(device, deviceState)
            }
            delay(20)
        }
    }

    private suspend fun disconnectDevice(deviceSerial: String) {
        fakeAdb.disconnectDevice(deviceSerial)

        while (true) {
            fakeAdb.bridge.devices.find {
                it.serialNumber == deviceSerial
            } ?: break
            delay(20)
        }
    }
}
