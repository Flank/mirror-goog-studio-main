/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.device.internal.adb.commands

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.device.internal.adb.ChannelConnection
import com.android.tools.device.internal.adb.DeviceHandle
import com.android.tools.device.internal.adb.PipeAdbServer
import com.google.common.base.Charsets
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Uninterruptibles
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TrackDevicesTest {
    @Rule @JvmField
    val testTimeout = Timeout(5, TimeUnit.SECONDS)

    private val trackDevicesCommand = "0012host:track-devices"

    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val listenerExecutor = VirtualTimeScheduler()
    private val deviceHandlesRef = AtomicReference<List<DeviceHandle>>(null)
    val trackDevices = TrackDevices({ handles -> deviceHandlesRef.set(handles) },
            listenerExecutor)

    @After
    fun tearDown() {
        assertThat(commandExecutor.shutdownNow()).isEmpty()
        assertThat(listenerExecutor.shutdownNow()).isEmpty()
    }

    @Test
    fun execute_nominal() {
        val response = """
        |emulator-5554          device product:sdk_google_phone_x86 model:Android_SDK_built_for_x86 device:generic_x86\n
        |412KPGS0147439         device usb:2-1.4.3 product:lenok model:G_Watch_R device:lenok\n
        |0871182e               device product:razorg model:Nexus_7 device:deb
        """.trimMargin()

        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                commandExecutor.submit { trackDevices.execute(connection) }

                // wait for the track devices command to be received by the server
                server.waitForCommand(trackDevicesCommand)

                server.respondWith(createOkayResponse(response))

                // wait for the callback to be registered, the timeout rule guards against this looping forever
                while (listenerExecutor.actionsQueued == 0L)
                    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS)

                listenerExecutor.advanceBy(1)
                val devices = deviceHandlesRef.get()

                assertThat(devices.size).isEqualTo(3)
                assertThat(devices[0].serial).isEqualTo("emulator-5554")
            }
        }
    }

    @Test
    fun execute_noDevices() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                commandExecutor.submit { trackDevices.execute(connection) }

                // wait for the track devices command to be received by the server
                server.waitForCommand(trackDevicesCommand)

                server.respondWith(createOkayResponse(""))

                // wait for the callback to be registered, the timeout rule guards against this looping forever
                while (listenerExecutor.actionsQueued == 0L)
                    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS)

                listenerExecutor.advanceBy(1)
                val devices = deviceHandlesRef.get()

                assertThat(devices.isEmpty()).isTrue()
            }
        }
    }

    @Test
    fun execute_error() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("FAIL0003err".toByteArray(Charsets.UTF_8))
                try {
                    trackDevices.execute(connection)
                } catch (e: IOException) {
                    assertThat(e.message).isEqualTo("Error connecting to adb server to track devices: err")
                }
            }
        }
    }

    @Test
    fun cancel_nominal() {
        PipeAdbServer().use { server ->
            val future =
                    ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                        val future = commandExecutor.submit { trackDevices.execute(connection) }

                        // wait for the track devices command to be received by the server
                        server.waitForCommand(trackDevicesCommand)

                        trackDevices.cancel()
                        future
                    }

            assertThat(future.isDone)
            try {
                future.get()
                fail("The command could have completed successfully since it was canceled")
            } catch (e: Exception) {
                assertThat(e.cause).isInstanceOf(IOException::class.java)
            }
        }

        assertThat(deviceHandlesRef.get()).isNull()
    }
}
