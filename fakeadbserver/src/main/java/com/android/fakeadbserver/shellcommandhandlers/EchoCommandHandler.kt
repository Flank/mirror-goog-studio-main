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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.google.common.base.Charsets
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * A [ShellHandler] that write its argument(s) to `stdout`, followed by a newline
 */
class EchoCommandHandler : SimpleShellHandler("echo") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState,
        args: String?
    ) {
        val output = responseSocket.getOutputStream()
        writeOkay(output)
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        args?.also { writer.write(args) }
        writer.write(shellNewLine(device))
        writer.flush()
    }
}
