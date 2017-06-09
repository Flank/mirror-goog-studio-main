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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import com.android.testutils.truth.MoreTruth
import com.android.tools.device.internal.adb.PipeAdbServer
import com.android.tools.device.internal.adb.ChannelConnection
import com.google.common.base.Charsets
import java.io.IOException
import java.util.Locale
import org.junit.Test

class ListDevicesTest {
    @Test
    fun list_nominal() {
        val response = """
        |emulator-5554          device product:sdk_google_phone_x86 model:Android_SDK_built_for_x86 device:generic_x86\n
        |412KPGS0147439         device usb:2-1.4.3 product:lenok model:G_Watch_R device:lenok\n
        |0871182e               device product:razorg model:Nexus_7 device:deb
        """.trimMargin()

        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith(createOkayResponse(response))
                val devices = ListDevices().execute(connection)
                assertThat(devices.size).isEqualTo(3)
                assertThat(devices[0].serial).isEqualTo("emulator-5554")
                assertThat(devices[1].serial).isEqualTo("412KPGS0147439")
                assertThat(devices[2].serial).isEqualTo("0871182e")
                MoreTruth.assertThat(devices[2].devicePath).isAbsent()
            }
        }
    }

    @Test
    fun list_empty() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith(createOkayResponse(""))
                val devices = ListDevices().execute(connection)
                assertThat(devices.isEmpty()).isTrue()
            }
        }

    }

    @Test
    fun list_error() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("FAIL0003err".toByteArray(Charsets.UTF_8))
                try {
                    ListDevices().execute(connection)
                    fail("Expected to throw an IOException when server returns a failure message")
                } catch (e: IOException) {
                    assertThat(e.message).isEqualTo("Error retrieving device list: err")
                }
            }
        }

    }

    private fun createOkayResponse(payload: String): ByteArray {
        val sb = StringBuilder()
        sb.append("OKAY")
        sb.append(String.format(Locale.US, "%04X", payload.length))
        sb.append(payload)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}