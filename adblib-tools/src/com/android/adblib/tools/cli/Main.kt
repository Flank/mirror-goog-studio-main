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
package com.android.adblib.tools.cli

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.tools.createStandaloneSession
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

object Main {

    private val deviceCommands = deviceCommandHandlers()
    private var deviceSelector : DeviceSelector = DeviceSelector.any()

    private fun printUsage() {
       println("Usage: adblib [FLAGS] CMD [CMD_FLAGS]")
        println(" * FLAGS (Device command only):")
        println("      -d: Target usb device (error if multiple devices).")
        println("      -e: Target tpc device (error if multiple devices)")
        println("      -s SERIAL: Target device identified by SERIAL")
        println("      -t TRANSPORT_ID: Target device identified by TRANSPORT_ID")
        println(" * CMD  : ")
        println("     install")
        println("     uninstall")
    }

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            // TODO Move to an AdbChannelProvider that knows how to spawn and ADB server.
            // This one assume it is already up and running which is fine for our current needs.
            val session = createStandaloneSession()
            var success = false
            session.use {
                session.host.use {
                    success = run(session, args)
                }
            }

            exitProcess(java.lang.Boolean.compare(success, true))
        }
    }

    private suspend fun run(session : AdbSession, args: Array<String>) : Boolean {
        val arguments = Arguments(args)

        // Consume all FLAGS arguments (stop at CMD_FLAGS)
        parseFlags(arguments)
        if (!arguments.hasMore()) {
            printUsage()
            return false
        }

        val commandName = arguments.next()
        if (!deviceCommands.containsKey(commandName)) {
            // TODO: Given that this is going to be a command-line utility, we should not throw
            // but rather have "pretty" error messages.
            throw IllegalStateException("Unknown command name '$commandName'")
        }

        val command = deviceCommands[commandName]!!
        return command.run(session, deviceSelector, arguments.remaining())
    }

    private suspend fun parseFlags(arguments: Arguments) {
       while(arguments.nextIsFlag()) {
           parseFlag(arguments)
       }
    }

    private suspend fun parseFlag(args: Arguments) {
        val arg = args.next()
        when (arg) {
            // -d use USB device (error if multiple devices connected)
            "-d" -> {
                deviceSelector = DeviceSelector.usb()
            }
            // -e use TCP/IP device (error if multiple TCP/IP devices available)
            "-e" -> {
                deviceSelector =DeviceSelector.local()
            }
            // -s SERIAL  use device with given serial (overrides $ANDROID_SERIAL)
            "-s" -> {
                val serial = args.peek()
                if (serial == "") {
                    throw IllegalStateException("Missing serial for flag '$arg'")
                }
                deviceSelector = DeviceSelector.fromSerialNumber(args.next())
            }
            // -t ID use device with given transport id
            "-t" -> {
                val transportId = args.peek()
                if (transportId == "") {
                    throw IllegalStateException("Missing transportId for flag '$arg'")
                }
                deviceSelector = DeviceSelector.fromTransportId(args.next().toLong())
            }
            else -> {
                throw IllegalStateException("Unknown flag $arg")
            }
        }
    }

    private fun deviceCommandHandlers(): Map<String, DeviceCommand> {
        val map = mutableMapOf<String, DeviceCommand>()
        addCommand(map, Install())
        addCommand(map, Uninstall())
        // Add here more commands as we author them
        return map
    }

    private fun addCommand(map: MutableMap<String, DeviceCommand>, cmd: DeviceCommand) {
        map[cmd.name] = cmd
    }
}

