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
import com.android.adblib.ddmlibcompatibility.testutils.connectTestDevice
import com.android.adblib.ddmlibcompatibility.testutils.createAdbLibSession
import com.android.adblib.ddmlibcompatibility.testutils.disconnectTestDevice
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.CoroutineTestUtils.yieldUntil
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.testing.FakeAdbRule
import junit.framework.Assert
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
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

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun testScopeIsCancelledWhenDeviceDisconnects() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(fakeAdb.createAdbLibSession())
        val clientManager = AdbLibClientManager(session)
        val listener = TestDeviceClientManagerListener()
        val (device, _) = fakeAdb.connectTestDevice()
        val deviceClientManager =
            clientManager.createDeviceClientManager(
                fakeAdb.bridge,
                device,
                listener
            )

        // Act
        fakeAdb.disconnectTestDevice(device.serialNumber)
        deviceClientManager.deviceScope.coroutineContext.job.join()

        // Assert
        Assert.assertFalse(deviceClientManager.deviceScope.isActive)
        Assert.assertEquals(0, listener.events.size)
    }

    @Test
    fun testClientListIsUpdatedWhenProcessesStart() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(fakeAdb.createAdbLibSession())
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
            deviceClientManager.clients.size == 2
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
        assertThrows { client.isDdmAware }
        assertThrows { client.kill() }
        Assert.assertTrue(client.isValid)
        assertThrows { client.debuggerListenPort }
        assertThrows { client.isDebuggerAttached }
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
        val session = registerCloseable(fakeAdb.createAdbLibSession())
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
        val session = registerCloseable(fakeAdb.createAdbLibSession())
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
            listener.events.isNotEmpty()
        }

        // Assert
        Assert.assertTrue(listener.events.size >= 1)
        Assert.assertSame(deviceClientManager, listener.events[0].deviceClientManager)
        Assert.assertSame(fakeAdb.bridge, listener.events[0].bridge)
        Assert.assertEquals(TestDeviceClientManagerListener.EventKind.PROCESS_LIST_UPDATED, listener.events[0].kind)
        Assert.assertNull(listener.events[0].client)
    }

    @Test
    fun testListenerIsCalledWhenProcessesEnd() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(fakeAdb.createAdbLibSession())
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
            listener.events.isNotEmpty()
        }
        val eventCount = listener.events.size

        deviceState.stopClient(10)
        deviceState.stopClient(12)
        yieldUntil {
            listener.events.size > eventCount
        }

        // Assert
        Assert.assertSame(deviceClientManager, listener.events.last().deviceClientManager)
        Assert.assertSame(fakeAdb.bridge, listener.events.last().bridge)
        Assert.assertEquals(TestDeviceClientManagerListener.EventKind.PROCESS_LIST_UPDATED, listener.events.last().kind)
        Assert.assertNull(listener.events.last().client)
    }

    @Test
    fun testListenerIsCalledWhenProcessPropertiesChange() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(fakeAdb.createAdbLibSession())
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
            // Temporary implementation updates process properties every 100 millis
            listener.events.any { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED }
                    && deviceClientManager.clients.any { it.clientData.clientDescription == "foo.bar" }
        }

        // Assert
        Assert.assertTrue(
            "Should have received a process list changed event",
            listener.events.any { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_LIST_UPDATED })

        Assert.assertTrue(
            "Should have received at least one process name changed event",
            listener.events.any { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED })

        val event = listener.events.last { it.kind == TestDeviceClientManagerListener.EventKind.PROCESS_NAME_UPDATED }
        Assert.assertSame(event.deviceClientManager, deviceClientManager)
        Assert.assertNotNull(event.client)
        Assert.assertEquals("FakeVM", event.client!!.clientData.vmIdentifier)
    }

    private fun assertThrows(block: () -> Unit) {
        runCatching(block).onSuccess {
            Assert.fail("Block should throw an exception")
        }
    }
}
