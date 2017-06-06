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

package com.android.tools.device.internal.adb

import com.android.tools.device.internal.adb.commands.CommandBuffer
import com.android.tools.device.internal.adb.commands.CommandResult
import com.android.tools.device.internal.adb.commands.HostService
import com.google.common.base.Charsets
import com.google.common.primitives.UnsignedInteger
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ChannelConnectionTest {
    @Rule @JvmField
    val testTimeout = Timeout(10, TimeUnit.SECONDS)

    @Test
    fun writeCommand_okResult() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("OKAY".toByteArray(Charsets.UTF_8))
                val someCommand = CommandBuffer().writeHostCommand(HostService.DEVICES)
                assertThat(connection.executeCommand(someCommand).isOk).isTrue()
            }
        }
    }

    @Test
    fun writeCommand_failureWithNoErrorData() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("FAIL0000".toByteArray(Charsets.UTF_8))
                val someCommand = CommandBuffer().writeHostCommand(HostService.DEVICES)
                val commandResult = connection.executeCommand(someCommand)
                assertThat(commandResult.isOk).isFalse()
                assertThat(commandResult.error).isNull()
            }
        }
    }

    @Test
    fun writeCommand_notEnoughData() {
        val executor = Executors.newSingleThreadExecutor(
                ThreadFactoryBuilder().setNameFormat("adb-exec-thread").build())

        val future: Future<CommandResult> = PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("OK".toByteArray(Charsets.UTF_8))
                val someCommand = CommandBuffer().writeHostCommand(HostService.DEVICES)
                val commandResult = executor.submit(Callable {
                    connection.executeCommand(someCommand)
                })

                try {
                    commandResult.get(500, TimeUnit.MILLISECONDS)
                    fail("Expected command to not complete since there wasn't enough data")
                } catch (expected: TimeoutException) {
                }

                commandResult
            }
        }

        try {
            future.get(1, TimeUnit.SECONDS)
            fail("command should not have completed since the response was incomplete")
        } catch (e: ExecutionException) {
            // when the connection is terminated, the future should completed with an exception
            assertThat(e.cause).isInstanceOf(IOException::class.java)
        }

        executor.shutdownNow()
    }

    @Test
    fun readUnsignedHexInt() {
        PipeAdbServer().use { server ->
            ChannelConnection(server.responseSource, server.commandSink).use { connection ->
                server.respondWith("f123".toByteArray(Charsets.UTF_8))
                val expected = UnsignedInteger.valueOf(0xf123)
                assertThat(connection.readUnsignedHexInt()).isEqualTo(expected)
            }
        }
    }
}
