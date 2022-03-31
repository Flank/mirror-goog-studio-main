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

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.PackageManager
import com.android.fakeadbserver.shellcommandhandlers.PackageManagerCommandHandler
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.regex.Pattern

//TODO: This AbbExecCommandHandler should be consolidated with CmdCommandHandler and
//      PackageManagerCommandHandler to centralize all install operations. This CL is
//      already very big so this will be done in a follow up.

class AbbExecCommandHandler : DeviceCommandHandler("abb_exec") {

    override fun invoke(server: FakeAdbServer, socket: Socket, device: DeviceState, args: String) {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            CommandHandler.writeOkay(output)

            val parameters = args.split(("\u0000"))
            val service = parameters[0]

            if (service == "package") {
                processPackage(parameters.slice(1 until parameters.size), output, input)
            }

        }
        catch (ignored: IOException) {
        }
    }

    private fun processPackage(args: List<String>, output: OutputStream, input : InputStream) {
        val cmd = args[0]

        var response = ""
        if (cmd == "path") {
            val appId = args[1]
            response = "/data/app/$appId/base.apk"
        } else if (cmd.startsWith("install-create")) {
            if (args.contains(PackageManager.BAD_FLAG)) {
                response = "Error: (requested to fail via flag))"
            } else {
                response = "Success: created install session [1234]"
            }
        } else if (cmd.startsWith("install-write")) {
            response = installWrite(java.lang.String.join(" ", args), input)
        } else if (cmd.startsWith("install-commit")) {
            if (args.contains(PackageManager.BAD_SESSION)) {
                response = "Error: (request with FAIL_ME session)"
            } else  {
                response = commit(args.drop(1), output, input)
            }
        } else if (cmd.startsWith("install-abandon")) {
            response = "Success\n"
        }
        writeString(output, response)
    }

    private fun commit(slice: List<String>, output: OutputStream, input: InputStream) : String{
        val sessionID = slice[0]
        if (sessionID == "FAIL_ME") {
            return "Error (requested a FAIL_ME session)\n"
        }
        return "Success\n"
    }

    private fun installWrite(args: String, input: InputStream): String {
        var parameters = args.split(" ")
        if (parameters.size == 0) {
            return "Malformed install-write request"
        }


        if (parameters[parameters.size - 1] != "-") {
            val sessionID = parameters[1]
            if (sessionID == PackageManager.BAD_SESSION) {
                return "Error: (request with FAIL_ME session)"
            }
            // This a a remote apk write (the apk is somewhere on the device, likely /data/local"..)
            // Use a random value
            return "Success: streamed 123456789 bytes\n"
        }

        // This is a streamed install
        val sizeIndex = parameters.indexOf("-S") + 1
        if (sizeIndex == 0) {
            return "Malformed install-write request"
        }

        val expectedBytesLength = parameters[sizeIndex].toInt()
        val buffer = ByteArray(1024)
        var totalBytesRead = 0
        while (totalBytesRead < expectedBytesLength) {
            val numRead: Int =
                input.read(buffer, 0, Math.min(buffer.size, expectedBytesLength - totalBytesRead))
            if (numRead < 0) {
                break
            }
            totalBytesRead += numRead
        }

        return "Success: streamed ${totalBytesRead} bytes\n"
    }
}
