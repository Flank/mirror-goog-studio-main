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
package com.android.fakeadbserver.execcommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket

class PingExecCommandHandler : SimpleExecHandler(PING_EXEC) {

    companion object {

        const val PING_EXEC = "ping"
        const val PING_EXEC_OUTPUT = "pong"
    }

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState,
        args: String?
    ) {
        val output = responseSocket.getOutputStream()
        CommandHandler.writeOkay(output)

        val response = PING_EXEC_OUTPUT
        CommandHandler.writeString(output, response)
    }
}
