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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.PortForwarder
import java.io.IOException
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Creates a reverse socket connection from this device to the host.
 */
internal class ReverseForwardCommandHandler : DeviceCommandHandler("reverse") {

    override fun invoke(
        server: FakeAdbServer,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        if (args == "list-forward") {
            // "reverse:list-forward"
            handleListForward(socket, device)
        } else if (args == "killforward-all") {
            // "reverse:killforward-all"
            handleKillForwardAll(socket, device)
        } else if (args.startsWith("killforward:")) {
            // "reverse:killforward:[from]"
            handleKillForward(socket, device, args)
        } else if (args.startsWith("forward:")) {
            // "reverse:forward:[from]:[to]
            handleForward(socket, device, args)
        } else {
            writeFailResponse(
                socket.getOutputStream(),
                "Unsupported reverse connection command: $args"
            )
        }
    }

    private fun handleForward(
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val stream = socket.getOutputStream()
        val newArgs = args.substringAfter("forward:")
        val forwardArgs = ForwardArgs.parse(newArgs)

        // The "from" is the device in case of reverse forward
        when (val deviceTransport = forwardArgs.fromTransport) {
            "tcp" -> {}
            else -> {
                writeFailResponse(
                    stream,
                    "Unsupported device socket specification: $deviceTransport"
                )
                return
            }
        }
        var devicePort: Int
        var devicePortToSendBack: Int? = null
        try {
            devicePort = forwardArgs.fromTransportArg.toInt()
            if (devicePort == 0) {
                // This is to emulate ADB Server behavior of picking an available port
                // This is currently hard-coded as we don't actually create sockets
                devicePort = 40200 + (Math.random() * 100).toInt()
                devicePortToSendBack = devicePort
            }
        } catch (ignored: NumberFormatException) {
            writeFailResponse(
                stream,
                "Invalid host port specified: " + forwardArgs.fromTransportArg
            )
            return
        }

        // The "to" is the local machine in case of reverse forward
        val forwarder = when (val hostTransport = forwardArgs.toTransport) {
            "tcp" -> try {
                val hostPort = forwardArgs.toTransportArg.toInt()
                PortForwarder.createPortForwarder(devicePort, hostPort)
            } catch (ignored: NumberFormatException) {
                writeFailResponse(
                    stream, "Invalid port specified: " + forwardArgs.toTransportArg
                )
                return
            }
            "local" -> PortForwarder.createUnixForwarder(devicePort, forwardArgs.toTransportArg)
            "jdwp" -> {
                writeFailResponse(stream, "JDWP connections not yet supported in fake ADB Server.")
                return
            }
            else -> {
                writeFailResponse(
                    stream,
                    "Unsupported transport specified: $hostTransport"
                )
                return
            }
        }
        val bindOk = device.addReversePortForwarder(forwarder, forwardArgs.norebind)
        try {
            if (bindOk) {
                // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
                // See
                // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
                writeOkay(stream)
                if (devicePortToSendBack != null) {
                    writeOkayResponse(stream, devicePortToSendBack.toString())
                } else {
                    writeOkay(stream)
                }
            } else {
                writeFailResponse(stream, "Could not bind to the specified forwarding ports.")
            }
        } catch (ignored: IOException) {
        }
    }

    private fun handleKillForward(
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val stream = socket.outputStream
        val newArgs = args.substringAfter("killforward:")
        val hostAddress = newArgs.split(":".toRegex()).toTypedArray()
        when (hostAddress[0]) {
            "tcp" -> {}
            "local" -> {
                writeFailResponse(
                    stream, "Host Unix domain sockets not supported in fake ADB Server."
                )
                return
            }
            else -> {
                writeFailResponse(stream, "Invalid host transport specified: " + hostAddress[0])
                return
            }
        }
        val hostPort: Int = try {
            hostAddress[1].toInt()
        } catch (ignored: NumberFormatException) {
            writeFailResponse(stream, "Invalid port specified: " + hostAddress[1])
            return
        }
        if (!device.removeReversePortForwarder(hostPort)) {
            writeFailResponse(stream, "Could not successfully remove forward.")
            return
        }
        // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
        // See
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
        writeOkay(stream)
        writeOkay(stream)
    }

    private fun handleKillForwardAll(socket: Socket, device: DeviceState) {
        val stream = socket.outputStream
        device.removeAllReversePortForwarders()
        // We send 2 OKAY answers: 1st OKAY is connect, 2nd OKAY is status.
        // See
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
        writeOkay(stream)
        writeOkay(stream)
    }

    private fun handleListForward(socket: Socket, device: DeviceState) {
        val stream = socket.outputStream
        val deviceListString = formatDeviceReverseForwardList(device)
        writeOkay(stream)
        write4ByteHexIntString(stream, deviceListString.length)
        stream.write(deviceListString.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun formatDeviceReverseForwardList(device: DeviceState): String {
        val builder = StringBuilder()
        for (portForwarder in device.allReversePortForwarders.values) {
            // The serial number of the transport is hard-coded:
            // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/daemon/usb.cpp;l=759
            builder.append("UsbFfs")
            builder.append(" ")
            builder.append("tcp:${portForwarder.source.port}")
            builder.append(" ")
            builder.append("tcp:${portForwarder.destination.port}")
            builder.append("\n")
        }

        // Remove trailing '\n' to match adb server behavior
        if (builder.isNotEmpty()) {
            builder.deleteCharAt(builder.length - 1)
        }
        return builder.toString()
    }
}
