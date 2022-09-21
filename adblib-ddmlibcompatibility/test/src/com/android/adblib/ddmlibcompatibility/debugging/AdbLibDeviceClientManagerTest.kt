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

import com.android.adblib.ddmlibcompatibility.testutils.FakeIDevice
import com.android.adblib.ddmlibcompatibility.testutils.TestDeviceClientManagerListener
import com.android.adblib.ddmlibcompatibility.testutils.TestDeviceClientManagerListener.EventKind.PROCESS_LIST_UPDATED
import com.android.adblib.ddmlibcompatibility.testutils.TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED
import com.android.adblib.ddmlibcompatibility.testutils.connectTestDevice
import com.android.adblib.ddmlibcompatibility.testutils.createAdbSession
import com.android.adblib.ddmlibcompatibility.testutils.disconnectTestDevice
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.testing.FakeAdbRule
import junit.framework.Assert
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

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

    @Test
    fun testTrackingWaitsUntilDeviceIsTracked() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()

        // Act
        val fakeIDevice = FakeIDevice("1234")
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                fakeIDevice,
                listener
            )

        // Delay a little, to make sure deviceClientManager has to wait a little
        // before it starts tracking the device. Note the delay here should be less than
        // the DEVICE_TRACKER_WAIT_TIMEOUT timeout used by AdbLibDeviceClientManager.
        delay(500)
        val clientSizeBeforeTracking = deviceClientManager.clients.size
        val (_, deviceState) = fakeAdb.connectTestDevice()
        deviceState.startClient(10, 0, "foo.bar", false)

        // If things work as expected, the client list should be updated
        yieldUntil {
            deviceClientManager.clients.size == 1
        }

        // Assert
        Assert.assertEquals(0, clientSizeBeforeTracking)
    }

    @Test
    fun testScopeIsCancelledWhenDeviceDisconnects() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )
        deviceState.startClient(10, 0, "foo.bar", false)
        deviceState.startClient(12, 0, "foo.bar.baz", false)
        yieldUntil {
            deviceClientManager.clients.size == 2
        }

        // Act
        fakeAdb.disconnectTestDevice(device.serialNumber)
        yieldUntil {
            listener.filterEvents { events -> events.any { it.kind == PROCESS_LIST_UPDATED } }
                    && deviceClientManager.clients.size == 0
        }

        // Assert
        listener.events()
            .lastOrNull { it.kind == PROCESS_LIST_UPDATED }
            ?.also { lastEvent ->
                Assert.assertSame(deviceClientManager, lastEvent.deviceClientManager)
                Assert.assertSame(fakeAdb.bridge, lastEvent.bridge)
            } ?: Assert.fail("No PROCESS_LIST_UPDATED event")
        Unit
    }

    @Test
    fun testClientListIsUpdatedWhenProcessesStart() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        deviceState.startClient(10, 0, "foo.bar", false)
        deviceState.startClient(12, 0, "foo.bar.baz", false)
        yieldUntil {
            // Wait for both processes to show up and for both the JDWP proxy
            // and process properties to be initialized.
            deviceClientManager.clients.size == 2 &&
                    deviceClientManager.clients.all {
                        it.debuggerListenPort > 0 && it.clientData.vmIdentifier != null }
        }

        // Assert
        val clients = deviceClientManager.clients
        Assert.assertEquals(2, clients.size)
        Assert.assertNotNull(clients.find { it.clientData.pid == 10 })
        Assert.assertNull(clients.find { it.clientData.pid == 11 })
        Assert.assertNotNull(clients.find { it.clientData.pid == 12 })

        val client = clients.first { it.clientData.pid == 10 }
        Assert.assertSame(device, client.device)
        Assert.assertNotNull(client.clientData)
        Assert.assertEquals(10, client.clientData.pid)
        Assert.assertTrue(client.isDdmAware)
        Assert.assertTrue(client.isValid)
        Assert.assertTrue(client.debuggerListenPort > 0)
        Assert.assertFalse(client.isDebuggerAttached)
        assertThrows { client.executeGarbageCollector() }
        assertThrows { client.startMethodTracer() }
        assertThrows { client.stopMethodTracer() }
        assertThrows { client.startSamplingProfiler(10, TimeUnit.SECONDS) }
        assertThrows { client.stopSamplingProfiler() }
        assertThrows { client.requestAllocationDetails() }
        assertThrows { client.enableAllocationTracker(false) }
        assertThrows { client.notifyVmMirrorExited() }
        assertThrows { client.listViewRoots(null) }
        assertThrows {
            client.captureView("v", "v1", object : DebugViewDumpHandler(10) {
                override fun handleViewDebugResult(data: ByteBuffer?) {
                }
            })
        }

        assertThrows {
            client.dumpViewHierarchy("v", false, false, false, object : DebugViewDumpHandler(10) {
                override fun handleViewDebugResult(data: ByteBuffer?) {
                }
            })
        }
        assertThrows { client.dumpDisplayList("v", "v1") }
    }

    @Test
    fun testClientListIsUpdatedWhenProcessesStop() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )
        deviceState.startClient(10, 0, "foo.bar", false)
        deviceState.startClient(12, 0, "foo.bar.baz", false)
        yieldUntil {
            deviceClientManager.clients.size == 2
        }

        // Act
        deviceState.stopClient(10)
        yieldUntil {
            deviceClientManager.clients.size == 1
        }

        // Assert
        Assert.assertEquals(12, deviceClientManager.clients.last().clientData.pid)
    }

    @Test
    fun testListenerIsCalledWhenProcessesStart() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        deviceState.startClient(10, 0, "foo.bar", false)
        deviceState.startClient(12, 0, "foo.bar.baz", false)
        yieldUntil {
            listener.filterEvents { events -> events.any { it.kind == PROCESS_LIST_UPDATED } }
        }

        // Assert
        val firstEvent = listener.events().first { it.kind == PROCESS_LIST_UPDATED }
        Assert.assertSame(deviceClientManager, firstEvent.deviceClientManager)
        Assert.assertSame(fakeAdb.bridge, firstEvent.bridge)
        Assert.assertEquals(PROCESS_LIST_UPDATED, firstEvent.kind)
        Assert.assertNull(firstEvent.client)
    }

    @Test
    fun testListenerIsCalledWhenProcessesEnd() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        deviceState.startClient(10, 0, "foo.bar", false)
        deviceState.startClient(12, 0, "foo.bar.baz", false)
        yieldUntil {
            listener.events().isNotEmpty()
        }
        listener.clearEvents()

        deviceState.stopClient(10)
        deviceState.stopClient(12)
        yieldUntil {
            listener.filterEvents { events -> events.any { it.kind == PROCESS_LIST_UPDATED } } &&
                deviceClientManager.clients.isEmpty()
        }

        // Assert
        val processListUpdatedEvent = listener.events().last { it.kind == PROCESS_LIST_UPDATED }
        Assert.assertSame(deviceClientManager, processListUpdatedEvent.deviceClientManager)
        Assert.assertSame(fakeAdb.bridge, processListUpdatedEvent.bridge)
        Assert.assertNull(processListUpdatedEvent.client)
    }

    @Test
    fun testListenerIsCalledWhenProcessPropertiesChange() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        deviceState.startClient(10, 0, "foo.bar", false)
        yieldUntil {
            listener.filterEvents { events -> events.any { it.kind == PROCESS_NAME_UPDATED } }
                    && listener.filterEvents { events -> events.any { it.kind == PROCESS_LIST_UPDATED } }
                    && deviceClientManager.clients.any { it.clientData.clientDescription == "foo.bar" }
        }

        // Assert
        Assert.assertTrue(
            "Should have received a process list changed event",
            listener.filterEvents { events ->
                events.any { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_LIST_UPDATED } })

        Assert.assertTrue(
            "Should have received at least one process name changed event",
            listener.filterEvents { events ->
                events.any { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED } })

        val event = listener.filterEvents { events -> events.last { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED } }
        Assert.assertSame(event.deviceClientManager, deviceClientManager)
        Assert.assertNotNull(event.client)
        Assert.assertEquals("FakeVM", event.client!!.clientData.vmIdentifier)
    }

    @Test
    fun testClientKillWorks() = runBlockingWithTimeout {
        // Prepare
        val session = fakeAdb.createAdbSession(closeables)
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, deviceState) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        deviceState.startClient(10, 0, "foo.bar", false)
        yieldUntil {
            // Wait for both processes to show up and for both the JDWP proxy
            // and process properties to be initialized.
            deviceClientManager.clients.size == 1 &&
                    deviceClientManager.clients.all {
                        it.debuggerListenPort > 0 && it.clientData.vmIdentifier != null
                    }
        }
        val client = deviceClientManager.clients.first { it.clientData.pid == 10 }
        client.kill()

        // Assert: Wait until process has disappeared from the list of Clients
        yieldUntil { deviceClientManager.clients.isEmpty() }
    }

    private fun assertThrows(block: () -> Unit) {
        runCatching(block).onSuccess {
            Assert.fail("Block should throw an exception")
        }
    }
}
