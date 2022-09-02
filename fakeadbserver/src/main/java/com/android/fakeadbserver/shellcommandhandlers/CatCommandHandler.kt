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
 * A [ShellHandler] that outputs all characters received from `stdin` back to `stdout`, one
 * line at a time, i.e. characters are written back to `stdout` only when a newline ("\n")
 * character is received from `stdin`.
 */
class CatCommandHandler : SimpleShellHandler("cat") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState,
        args: String?
    ) {
        try {
            val output = responseSocket.getOutputStream()
            writeOkay(output)
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            InputStreamReader(responseSocket.getInputStream(), Charsets.UTF_8).use { reader ->
                // Note: We process each character individually to ensure we send back
                //       all character without any loss and/or conversion.
                val sb = StringBuilder()
                var ch = reader.read()
                while (ch != -1) {
                    // Translate '\n' to '\r\n' on older devices
                    if (ch != '\n'.code) {
                        sb.append(ch.toChar())
                    } else {
                        sb.append(shellNewLine(device))
                        writer.write(sb.toString())
                        writer.flush()
                        sb.setLength(0)
                    }
                    ch = reader.read()
                }
                if (sb.isNotEmpty()) {
                    writer.write(sb.toString())
                    writer.flush()
                }
            }
        } catch (ignored: IOException) {
        }
    }
}
