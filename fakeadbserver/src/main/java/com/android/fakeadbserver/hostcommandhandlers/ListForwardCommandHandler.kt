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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Sends a list of all forward socket connections for all active devices
 */
internal class ListForwardCommandHandler : HostCommandHandler() {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        val stream = responseSocket.getOutputStream()
        val deviceListString = formatList(fakeAdbServer.deviceListCopy.get())
        writeOkay(stream)
        write4ByteHexIntString(stream, deviceListString.length)
        stream.write(deviceListString.toByteArray(StandardCharsets.US_ASCII))
        return false
    }

    private fun formatList(deviceList: List<DeviceState>): String {
        val builder = StringBuilder()
        for (deviceState in deviceList) {
            for (portForwarder in deviceState.allPortForwarders.values) {
                builder.append(deviceState.deviceId)
                builder.append(" ")
                builder.append("tcp:${portForwarder.source.port}")
                builder.append(" ")
                builder.append("tcp:${portForwarder.destination.port}")
                builder.append("\n")
            }
        }

        // Remove trailing '\n' to match adb server behavior
        if (builder.isNotEmpty()) {
            builder.deleteCharAt(builder.length - 1)
        }
        return builder.toString()
    }

    companion object {

        const val COMMAND = "list-forward"
    }
}
