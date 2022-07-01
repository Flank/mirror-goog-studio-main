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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbSession
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.toChannelReader
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A connection to an emulator's console, allowing control of the emulator.
 */
class EmulatorConsole constructor(
    private val adbChannel: AdbChannel,
    private val authTokenProvider: suspend () -> String
) : AutoCloseable {

    private val workBuffer = ResizableBuffer()
    private val channelReader =
        adbChannel.toChannelReader(EMULATOR_CONSOLE_CHARSET, EMULATOR_CONSOLE_NEWLINE)

    suspend fun authenticate(): EmulatorCommandResult {
        val authToken = authTokenProvider()
        return sendCommand("auth $authToken")
    }

    /** Returns the AVD name. */
    suspend fun avdName(): String =
        sendCommand("avd name").throwOnError().outputLines.firstOrNull()
            ?: throw EmulatorCommandException("No output from \"avd name\"")

    /**
     * Returns the absolute path to the virtual device in the file system. The path is operating system
     * dependent; it will have / name separators on Linux and \ separators on Windows.
     *
     * @throws EmulatorCommandException If the command failed or if the emulator's version is older than 30.0.18
     */
    suspend fun avdPath(): Path =
        Paths.get(
            sendCommand("avd path").throwOnError().outputLines.firstOrNull()
                ?: throw EmulatorCommandException("No output from \"avd path\"")
        )

    suspend fun kill() {
        sendCommand("kill").throwOnError()
    }

    /**
     * Starts recording the emulator screen to the given path. The path must end with .webm and have
     * no whitespace.
     */
    suspend fun startScreenRecording(path: Path, vararg options: String) {
        val pathString = path.toString()
        if (pathString.chars().anyMatch(Character::isWhitespace)) {
            throw EmulatorCommandException("Whitespace not allowed in path string")
        }
        sendCommand("screenrecord start ${options.joinToString(" ")} $pathString").throwOnError()
    }

    suspend fun stopScreenRecording() {
        sendCommand("screenrecord stop").throwOnError()
    }

    /**
     * Sends the given command string, then parses and returns the response.
     */
    suspend fun sendCommand(command: String): EmulatorCommandResult {
        workBuffer.clear()
        workBuffer.appendString(command, EMULATOR_CONSOLE_CHARSET)
        workBuffer.appendString(EMULATOR_CONSOLE_NEWLINE, EMULATOR_CONSOLE_CHARSET)

        adbChannel.writeExactly(workBuffer.forChannelWrite())

        return readResponse()
    }

    internal suspend fun readResponse(): EmulatorCommandResult {
        val outputLines = mutableListOf<String>()
        while (true) {
            val line =
                channelReader.readLine() ?: return EmulatorCommandResult(
                    outputLines,
                    "Connection closed unexpectedly"
                )
            if (line.startsWith("KO: ")) {
                return EmulatorCommandResult(outputLines, line.substring(4).trim())
            } else if (line.startsWith("OK")) {
                return EmulatorCommandResult(outputLines, null)
            } else {
                outputLines.add(line)
            }
        }
    }

    override fun close() {
        adbChannel.close()
    }

    /**
     * The result of invoking an emulator command. Emulator responses contain zero or more lines
     * of output, followed by "OK" on success, or "KO: <error message>" on error.
     *
     * @property outputLines the output of the command prior to the success / error code, without
     * line terminators.
     * @property error if present, indicates command failure and contains the error message.
     */
    class EmulatorCommandResult(val outputLines: List<String>, val error: String?) {

        fun isOk() = error == null
        fun isError() = error != null

        fun throwOnError(): EmulatorCommandResult {
            if (error != null) {
                throw EmulatorCommandException(error)
            }
            return this
        }
    }
}

private const val AUTH_REQUIRED = "Android Console: Authentication required"
private val EMULATOR_CONSOLE_CHARSET = StandardCharsets.UTF_8
private const val EMULATOR_CONSOLE_NEWLINE = "\r\n"

/**
 * Attempts to connect to an emulator console at the supplied address, authenticating
 * if required.
 *
 * @throws EmulatorCommandException if authentication fails
 */
suspend fun AdbSession.openEmulatorConsole(
    address: InetSocketAddress,
    authTokenPath: Path = defaultAuthTokenPath()
): EmulatorConsole =
    openEmulatorConsole(address) {
        channelFactory.openFile(authTokenPath)
            .toChannelReader()
            .readLine()
            ?.trim() ?: ""
    }

/**
 * Attempts to connect to an emulator console at the supplied address, authenticating
 * if required.
 *
 * @throws EmulatorCommandException if authentication fails
 */
suspend fun AdbSession.openEmulatorConsole(
    address: InetSocketAddress,
    authTokenProvider: suspend () -> String
): EmulatorConsole {
    val channelProvider =
        AdbChannelProviderFactory.createConnectAddresses(host) {
            listOf(address)
        }

    val console = EmulatorConsole(channelProvider.createChannel(), authTokenProvider)

    val result = console.readResponse().throwOnError()
    if (result.outputLines.any { it.contains(AUTH_REQUIRED) }) {
        console.authenticate().throwOnError()
    }
    return console

}

fun localConsoleAddress(port: Int) =
    InetSocketAddress(InetAddress.getLoopbackAddress(), port)

fun defaultAuthTokenPath(): Path =
    Paths.get(System.getProperty("user.home"), ".emulator_console_auth_token")


/** Simple wrapper around EmulatorConsole for manual integration testing. */
fun main(args: Array<String>) {
    runBlocking {
        FakeAdbSession().openEmulatorConsole(localConsoleAddress(args[0].toInt())).use {
            println("Connected to emulator")
            println("AVD name: ${it.avdName()}")
            println("AVD path: ${it.avdPath()}")
        }
    }
}

class EmulatorCommandException(error: String) : Exception(error)
