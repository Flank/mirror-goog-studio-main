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
package com.android.adblib.tools

import com.android.adblib.testing.FakeAdbLibSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.EmptyCoroutineContext

class EmulatorConsoleTest {

    private val scope = CoroutineScope(EmptyCoroutineContext)
    private val fakeEmulator = FakeEmulator()

    @After
    fun tearDown() {
        scope.cancel(null)
    }

    class FakeEmulator : Runnable {

        val server = ServerSocket(0)
        val port = server.localPort
        var socket: Socket? = null

        val inputQueue = LinkedBlockingQueue<String>()
        val outputQueue = LinkedBlockingQueue<String>()

        fun start() {
            Thread(this, "FakeEmulator").start()
        }

        override fun run() {
            try {
                val socket = server.accept()
                this.socket = socket
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = PrintWriter(socket.getOutputStream())
                output.write(outputQueue.take())
                output.flush()
                while (true) {
                    val line = input.readLine() ?: return
                    inputQueue.add(line)
                    val response = outputQueue.take()
                    output.write(response)
                    output.flush()
                }
            } catch (e: IOException) {
                // Ignore socket closing
            }
        }

        fun close() {
            socket?.close()
        }
    }

    fun connectNoAuth(): EmulatorConsole {
        fakeEmulator.start()
        fakeEmulator.outputQueue.put("Hello\r\nOK\r\n")

        return runBlockingWithTimeout {
            FakeAdbLibSession().openEmulatorConsole(
                localConsoleAddress(fakeEmulator.port),
                { "" })
        }
    }

    @Test
    fun zeroLineOutput() {
        val console = connectNoAuth()
        val rotateAsync = scope.async {
            console.sendCommand("rotate")
        }

        assertEquals("rotate", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        val result = runBlockingWithTimeout { rotateAsync.await() }
        assertEquals(0, result.outputLines.size)
        assertNull(result.error)
    }

    @Test
    fun oneLineOutput() {
        val console = connectNoAuth()

        val avdPathAsync = scope.async {
            console.sendCommand("avd path")
        }

        assertEquals("avd path", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("/tmp/avd/nexus_5.avd\r\nOK\r\n")

        val avdPath = runBlockingWithTimeout { avdPathAsync.await() }

        assertFalse(avdPath.isError())
        assertEquals(listOf("/tmp/avd/nexus_5.avd"), avdPath.outputLines)

        fakeEmulator.close()
    }

    @Test
    fun screenRecord() {
        val console = connectNoAuth()
        val recordAsync = scope.async {
            console.startScreenRecording(Paths.get("test.webm"), "--size 800x600")
        }

        assertEquals("screenrecord start --size 800x600 test.webm", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        runBlockingWithTimeout { recordAsync.await() }
    }

    @Test
    fun error() {
        val console = connectNoAuth()
        val commandAsync = scope.async {
            console.sendCommand("xyzzy")
        }

        assertEquals("xyzzy", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("KO: unknown command\r\n")

        val result = runBlockingWithTimeout { commandAsync.await() }
        assertEquals(0, result.outputLines.size)
        assertEquals("unknown command", result.error)
    }

    @Test
    fun connectAuth() {
        fakeEmulator.start()
        fakeEmulator.outputQueue.put("Android Console: Authentication required\r\nOK\r\n")

        val consoleAsync = scope.async {
            FakeAdbLibSession().openEmulatorConsole(
                localConsoleAddress(fakeEmulator.port),
                { "my secret token" })
        }

        assertEquals("auth my secret token", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        runBlockingWithTimeout { consoleAsync.await() }
    }

    fun <T> runBlockingWithTimeout(block: suspend CoroutineScope.() -> T) =
        runBlocking {
            withTimeout(5000) {
                block()
            }
        }
}
