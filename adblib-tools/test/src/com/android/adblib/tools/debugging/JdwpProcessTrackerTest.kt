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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class JdwpProcessTrackerTest : AdbLibToolsTestBase() {

    @Test
    fun testJdwpProcessTrackerWorks(): Unit = runBlockingWithTimeout {
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
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<List<JdwpProcess>>()
        launch {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            yieldUntil {
                val size = listOfProcessList.size
                size == 1
            }

            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            Assert.assertNotNull(fakeDevice.getClient(pid10))
            Assert.assertNotNull(fakeDevice.getClient(pid11))
            yieldUntil { listOfProcessList.size == 2 }

            // Note: Depending on how fast FakeAdbServer is, adblib may get one or two
            //       jdwp tracking event
            fakeDevice.stopClient(pid10)
            fakeDevice.stopClient(pid11)
            Assert.assertNull(fakeDevice.getClient(pid10))
            Assert.assertNull(fakeDevice.getClient(pid11))
        }

        val jdwpTracker = JdwpProcessTracker(hostServices.session, deviceSelector)
        // Collecting the flow deterministically is a little tricky, as the list of events
        // in the flow depend on how fast FakeAdbServer emits events from the "track-jdwp"
        // event and how fast adblib collects and emits these events in the jdwp tracker
        // flow.
        jdwpTracker.createFlow().takeWhile { processList ->
            // The goal here is to collect 3 list of processes in `listOfProcessList`
            // * One with a single process
            // * One with 2 processes
            // * One with no processes (after both processes are stopped)
            // The flow itself may emit a variable amount of "no process" lists in the flow,
            // then a variable amount of "1 process" lists in the flow, then a variable
            // amount of "2 processes" lists, then a variable amount of "no process" lists.
            when (listOfProcessList.size) {
                // When the list is empty, only add a non-empty process list, it should be a list
                // of 1 process.
                0 -> {
                    if (processList.isNotEmpty()) {
                        assert(processList.size == 1)
                        listOfProcessList.add(processList)
                    }
                    true
                }
                // When the list has one element, add an element only if the process list
                // contains 2 elements.
                1 -> {
                    // The flow may emit a list of 1 process multiple times, because
                    // FakeAdbServer sometimes emit the same list multiple times
                    if (processList.size == 2) {
                        listOfProcessList.add(processList)
                    }
                    true
                }
                // When the list has 2 elements, wait until we get an empty process list
                // stop collecting at that point.
                2 -> {
                    if (processList.isEmpty()) {
                        // There may be 1 or 2 lists depending on how fast the flow
                        // catches up with the 2 process terminations.
                        listOfProcessList.add(processList)
                        false
                    } else {
                        true
                    }
                }
                else -> {
                    Assert.fail("Should not reach")
                    false
                }
            }
        }.collect()

        // Assert: We should have 3 lists: 1 process, 2 processes, empty list.
        Assert.assertTrue(listOfProcessList.size == 3)

        // First list has one process
        Assert.assertEquals(1, listOfProcessList[0].size)
        Assert.assertEquals(listOf(pid10), listOfProcessList[0].map { it.pid }.toList())

        // Second list has 2 processes
        Assert.assertEquals(2, listOfProcessList[1].size)
        Assert.assertEquals(listOf(pid10, pid11), listOfProcessList[1].map { it.pid }.toList())

        // Last list is empty
        Assert.assertEquals(0, listOfProcessList[2].size)

        // Ensure JdwpProcess instances are re-used across flow changes
        Assert.assertSame(listOfProcessList[0].first { it.pid == pid10 },
                          listOfProcessList[1].first { it.pid == pid10 })

        val process10 = listOfProcessList[0].first { it.pid == pid10 }
        Assert.assertEquals(deviceSelector, process10.device)
        Assert.assertEquals(pid10, process10.pid)
        Assert.assertFalse(process10.scope.isActive)
    }

    @Test
    fun testJdwpProcessTrackerCollectsProcessProperties() = runBlockingWithTimeout {
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
        val pid10 = 10
        val pid11 = 11
        fakeDevice.startClient(pid10, 100, "a.b.c", false)
        fakeDevice.startClient(pid11, 101, "a.b.c.e", true)
        val jdwpTracker = JdwpProcessTracker(hostServices.session, deviceSelector)

        // Act
        val processListFlow = jdwpTracker.createFlow()
        val (process10, process11) = processListFlow
            .mapNotNull { list ->
                if (list.size == 2) {
                    val process10 = list.first { it.pid == pid10 }
                    val process11 = list.first { it.pid == pid11 }
                    yieldUntil {
                        (process10.propertiesFlow.value.processName != null) &&
                                (process11.propertiesFlow.value.processName != null)
                    }
                    Pair(process10, process11)
                } else {
                    null
                }
            }.first()

        // Assert
        Assert.assertEquals(pid10, process10.pid)
        Assert.assertEquals("a.b.c", process10.propertiesFlow.value.processName)
        Assert.assertEquals(100, process10.propertiesFlow.value.userId)

        Assert.assertEquals(pid11, process11.pid)
        Assert.assertEquals("a.b.c.e", process11.propertiesFlow.value.processName)
        Assert.assertEquals(101, process11.propertiesFlow.value.userId)
    }

    @Test
    fun testJdwpProcessTrackerFlowStopsWhenDeviceDisconnects(): Unit = runBlockingWithTimeout {
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
        val pid10 = 10
        val pid11 = 11

        // Act
        val listOfProcessList = CopyOnWriteArrayList<List<JdwpProcess>>()
        val jdwpTracker = JdwpProcessTracker(hostServices.session, deviceSelector)
        launch {
            fakeDevice.startClient(pid10, 0, "a.b.c", false)
            fakeDevice.startClient(pid11, 0, "a.b.c.e", false)
            yieldUntil { listOfProcessList.size >= 1 }

            fakeAdb.disconnectDevice(fakeDevice.deviceId)
        }

        jdwpTracker.createFlow().collect {
            listOfProcessList.add(it)
        }

        // Assert
        // We don't assert anything, the fact we reached this point means the
        // flow was cancelled when the device was disconnected.
    }

    @Test
    fun testJdwpProcessTrackerFlowIsExceptionTransparent(): Unit = runBlockingWithTimeout {
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
        val pid10 = 10

        // Act
        exceptionRule.expect(Exception::class.java)
        exceptionRule.expectMessage("My Test Exception")
        fakeDevice.startClient(pid10, 0, "a.b.c", false)

        val jdwpTracker = JdwpProcessTracker(hostServices.session, deviceSelector)
        jdwpTracker.createFlow().collect {
            throw Exception("My Test Exception")
        }

        // Assert (should not reach)
        Assert.fail()
    }

    @Test
    fun testJdwpProcessTrackerFlowICanBeCancelled(): Unit = runBlockingWithTimeout {
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
        fakeDevice.startClient(pid10, 0, "a.b.c", false)

        val jdwpTracker = JdwpProcessTracker(hostServices.session, deviceSelector)
        jdwpTracker.createFlow().collect {
            cancel("My Test Exception")
        }

        // Assert (should not reach)
        Assert.fail()
    }
}
