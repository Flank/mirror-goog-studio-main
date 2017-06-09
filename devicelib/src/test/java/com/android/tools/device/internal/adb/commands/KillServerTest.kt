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
import com.google.common.primitives.Bytes
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test

class KillServerTest {
    @Test
    @Ignore // flaky
    fun execute_killCommand() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("OKAY".toByteArray(Charsets.UTF_8))
                KillServer().execute(connection)
                val receivedCommand = Bytes.toArray(server.commandBuffer).toString(Charsets.UTF_8)
                assertThat(receivedCommand).isEqualTo("0009host:kill")
            }
        }
    }
}
