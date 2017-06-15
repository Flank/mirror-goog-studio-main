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

import com.android.tools.device.internal.adb.AdbFeature
import com.android.tools.device.internal.adb.ChannelConnection
import com.android.tools.device.internal.adb.PipeAdbServer
import com.google.common.base.Charsets
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.TimeUnit

class HostFeaturesTest {
    @Rule @JvmField
    val testTimeout = Timeout(5, TimeUnit.SECONDS)

    private val featuresCommand = HostFeatures()

    @Test
    fun execute_nominal() {
        val data = mapOf(
                "OKAY0000" to setOf(),
                "OKAY000Cshell_v2,cmd" to setOf(AdbFeature.SHELL2, AdbFeature.CMD),
                "OKAY000Bshell_v2,cm" to setOf(AdbFeature.SHELL2, AdbFeature.UNKNOWN)
        )

        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                for ((k, v) in data) {
                    server.respondWith(k.toByteArray(Charsets.UTF_8))
                    Truth.assertThat(featuresCommand.execute(connection)).isEqualTo(v)
                }
            }
        }
    }

    @Test
    fun execute_error() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("FAIL0003err".toByteArray(Charsets.UTF_8))
                try {
                    featuresCommand.execute(connection)
                    Assert.fail("Expected to throw an IOException when server returns a failure message")
                } catch (e: IOException) {
                    Truth.assertThat(e.message).isEqualTo("Error retrieving feature set: err")
                }
            }
        }

    }
}