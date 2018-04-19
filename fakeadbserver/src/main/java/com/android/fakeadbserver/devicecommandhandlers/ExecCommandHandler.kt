/*
 * Copyright (C) 2018 The Android Open Source Project
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
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.regex.Pattern

class ExecCommandHandler : DeviceCommandHandler() {
  companion object {
    const val COMMAND = "exec"
  }

  override fun invoke(fakeAdbServer: FakeAdbServer, responseSocket: Socket, device: DeviceState, args: String): Boolean {
    try {
      val output = responseSocket.getOutputStream()

      CommandHandler.writeOkay(output)

      val response: String = when {
        args.startsWith("cmd package install-write") -> installWrite(args, responseSocket.getInputStream())
        else -> ""
      }

      CommandHandler.writeString(output, response)
    }
    catch (ignored: IOException) {
    }
    return false
  }

  /**
   * Handler for commands that look like:
   *
   *    exec:cmd package install-write -S 1289508 548838628 0_base-debug -
   *
   * `args` would be "cmd package install-write -S 1289508 548838628 0_base-debug -" in
   * the above example.
   */
  private fun installWrite(args: String, input: InputStream): String {
    val streamLengthExtractor = Pattern.compile("cmd package install-write\\s+-S\\s+(\\d+).*")
    val streamLengthMatcher = streamLengthExtractor.matcher(args)
    streamLengthMatcher.find()

    val expectedBytesLength = if (streamLengthMatcher.groupCount() < 1) {
      0
    } else {
      try {
        streamLengthMatcher.group(1).toInt()
      } catch(numFormatException: NumberFormatException) {
        0
      }
    }

    val buffer= ByteArray(1024)
    var totalBytesRead = 0
    while (totalBytesRead < expectedBytesLength) {
      val numRead: Int = input.read(buffer, 0, Math.min(buffer.size, expectedBytesLength - totalBytesRead))
      if (numRead < 0) {
        break
      }
      totalBytesRead += numRead
    }

    return "Success: streamed ${totalBytesRead} bytes\n"
  }
}
