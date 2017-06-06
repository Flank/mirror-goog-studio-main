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

import com.android.tools.device.internal.adb.PipeAdbServer
import com.android.tools.device.internal.adb.ChannelConnection
import com.google.common.base.Charsets
import com.google.common.primitives.UnsignedInteger
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.TimeUnit

class ServerVersionTest {
    @Rule @JvmField
    val testTimeout = Timeout(5, TimeUnit.SECONDS)

    private val versionCommand: ServerVersion = ServerVersion()

    @Test
    fun version_nominal() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("OKAY00040024".toByteArray(Charsets.UTF_8))
                val expected = UnsignedInteger.valueOf(0x24)
                assertThat(versionCommand.execute(connection)).isEqualTo(expected)
            }
        }
    }

    @Test
    fun version_failure() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("FAIL0003Err".toByteArray(Charsets.UTF_8))
                try {
                    versionCommand.execute(connection)
                } catch (e: IOException) {
                    assertThat(e.message).isEqualTo("Err")
                }
            }
        }
    }
}
