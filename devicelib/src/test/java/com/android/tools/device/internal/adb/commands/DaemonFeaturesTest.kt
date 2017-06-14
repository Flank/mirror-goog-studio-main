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

import com.android.tools.device.internal.adb.ChannelConnection
import com.android.tools.device.internal.adb.DeviceHandle
import com.android.tools.device.internal.adb.PipeAdbServer
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DaemonFeaturesTest {
    @Rule @JvmField
    val testTimeout = Timeout(5, TimeUnit.SECONDS)

    private val commandExecutor = Executors.newSingleThreadExecutor()

    @After
    fun tearDown() {
        Truth.assertThat(commandExecutor.shutdownNow()).isEmpty()
    }

    @Test
    fun getQuery_nominal() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                commandExecutor.submit {
                    DaemonFeatures(DeviceHandle.create("DEADBEEF       device")).execute(connection)
                }
                server.waitForCommand("001Dhost-serial:DEADBEEF:features")
            }
        }
    }
}