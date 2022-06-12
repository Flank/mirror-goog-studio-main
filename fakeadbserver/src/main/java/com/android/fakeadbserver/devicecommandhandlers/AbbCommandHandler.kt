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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.services.ShellProtocolServiceOutput
import java.net.Socket

class AbbCommandHandler : DeviceCommandHandler(COMMAND) {
    companion object {

        const val COMMAND = "abb"
        const val SEPARATOR = "\u0000"
    }

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        // Acknowledge "abb_exec" is supported
        writeOkay(socket.getOutputStream())

        // Save command to logs so tests can consult them.
        device.addAbbLog(args)

        // Wrap stdin/stdout and execute abb command
        val serviceOutput = ShellProtocolServiceOutput(socket)
        device.serviceManager.processCommand(args.split("\u0000"), serviceOutput)
    }
}
