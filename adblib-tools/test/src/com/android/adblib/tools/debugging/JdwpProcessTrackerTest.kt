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
package com.android.adblib.tools.debugging

import com.android.adblib.DeviceSelector
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.CoroutineTestUtils.yieldUntil
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.Collections

class JdwpProcessTrackerTest : AdbLibToolsTestBase() {

    @Test
    fun testJdwpProcessTrackerWorks() {
        val deviceID = "1234"
        val theOneFeatureSupported = "push_sync"
        val features = setOf(theOneFeatureSupported)
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildWithFeatures(features).start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val hostServices = createHostServices(fakeAdb)
        //hostServices.session.host.setTestLoggerMinLevel(AdbLogger.Level.VERBOSE)
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = synchronizedList<List<JdwpProcess>>()
        runBlockingWithTimeout {
            launch {
                fakeDevice.startClient(pid10, 0, "a.b.c", false)
                yieldUntil { listOfProcessList.size == 1 }

                fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
                yieldUntil { listOfProcessList.size == 2 }

                // Note: The flow may emit 1 or 2 lists depending on how fast clients
                // are stopped.
                fakeDevice.stopClient(pid10)
                fakeDevice.stopClient(pid11)
            }

            val jdwpTracker = JdwpTracker(hostServices.session, deviceSelector)
            jdwpTracker.createFlow().takeWhile { processList ->
                listOfProcessList.add(processList)
                listOfProcessList.size < 2 || processList.isNotEmpty()
            }.collect()
        }

        // Assert
        Assert.assertTrue(listOfProcessList.size >= 3)

        // Help debug test failures:
        listOfProcessList.forEachIndexed { index, jdwpProcesses ->
            println("Process list #$index: $jdwpProcesses")
        }
        // First list has one process
        Assert.assertEquals(1, listOfProcessList[0].size)
        Assert.assertEquals(listOf(pid10), listOfProcessList[0].map { it.pid }.toList())

        // Second list has 2 processes
        Assert.assertEquals(2, listOfProcessList[1].size)
        Assert.assertEquals(listOf(pid10, pid11), listOfProcessList[1].map { it.pid }.toList())

        // Last list is empty
        Assert.assertEquals(0, listOfProcessList.last().size)

        // Ensure JdwpProcess instances are re-used across flow changes
        Assert.assertSame(listOfProcessList[0].first { it.pid == pid10 },
                          listOfProcessList[1].first { it.pid == pid10 })

        val process10 = listOfProcessList[0].first { it.pid == pid10 }
        Assert.assertEquals(deviceSelector, process10.device)
        Assert.assertEquals(pid10, process10.pid)
        Assert.assertFalse(process10.scope.isActive)
    }

    @Test
    fun testJdwpProcessTrackerFlowStopsWhenDeviceDisconnects() {
        val deviceID = "1234"
        val theOneFeatureSupported = "push_sync"
        val features = setOf(theOneFeatureSupported)
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildWithFeatures(features).start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val hostServices = createHostServices(fakeAdb)
        //hostServices.session.host.setTestLoggerMinLevel(AdbLogger.Level.VERBOSE)
        val pid10 = 10
        val pid11 = 11

        // Act
        runBlocking {
            val listOfProcessList = synchronizedList<List<JdwpProcess>>()
            val jdwpTracker = JdwpTracker(hostServices.session, deviceSelector)
            launch {
                fakeDevice.startClient(pid10, 0, "a.b.c", false)
                fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
                yieldUntil { listOfProcessList.size >= 1 }

                fakeAdb.disconnectDevice(fakeDevice.deviceId)
            }

            jdwpTracker.createFlow().collect {
                listOfProcessList.add(it)
            }
        }

        // Assert
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testJdwpProcessTrackerFlowIsExceptionTransparent() {
        val deviceID = "1234"
        val theOneFeatureSupported = "push_sync"
        val features = setOf(theOneFeatureSupported)
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildWithFeatures(features).start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val hostServices = createHostServices(fakeAdb)
        //hostServices.session.host.setTestLoggerMinLevel(AdbLogger.Level.VERBOSE)
        val pid10 = 10

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("My Test Exception")
        runBlocking {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)

            val jdwpTracker = JdwpTracker(hostServices.session, deviceSelector)
            jdwpTracker.createFlow().collect {
                throw Exception("My Test Exception")
            }
        }

        // Assert (should not reach)
        Assert.fail()
    }

    @Test
    fun testJdwpProcessTrackerFlowICanBeCancelled() {
        val deviceID = "1234"
        val theOneFeatureSupported = "push_sync"
        val features = setOf(theOneFeatureSupported)
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildWithFeatures(features).start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val hostServices = createHostServices(fakeAdb)
        //hostServices.session.host.setTestLoggerMinLevel(AdbLogger.Level.VERBOSE)
        val pid10 = 10

        // Act
        exceptionRule.expect(CancellationException()::class.java)
        exceptionRule.expectMessage("My Test Exception")
        runBlocking {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)

            val jdwpTracker = JdwpTracker(hostServices.session, deviceSelector)
            jdwpTracker.createFlow().collect {
                cancel("My Test Exception")
            }
        }

        // Assert (should not reach)
        Assert.fail()
    }

    private fun <T> synchronizedList(): MutableList<T> {
        return Collections.synchronizedList(mutableListOf())
    }
}
