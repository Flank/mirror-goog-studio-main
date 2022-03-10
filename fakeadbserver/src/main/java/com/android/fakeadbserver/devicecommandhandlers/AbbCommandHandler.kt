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
import com.android.fakeadbserver.shellv2commandhandlers.ShellV2Protocol
import java.io.IOException
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
    try {
        val protocol = ShellV2Protocol(socket)
        protocol.writeOkay()

        val response: String = when {
            args.startsWith("package${SEPARATOR}install-create") -> installMultiple()
            args.startsWith("package${SEPARATOR}install-commit") -> installCommit()
            else -> ""
        }

      protocol.writeStdout(response.toByteArray())
      protocol.writeExitCode(0)
    } catch(ignored: IOException) {
    }
  }

  /**
   * Handler for commands that look like:
   *
   *    adb abb package install-multiple -r -t -S 1234
   */
  private fun installMultiple(): String {
    return "Success: created install session [1234]"
  }

  /**
   * handler for commands that look like:
   *
   *    adb abb package install-commit XXXXX
   */
  private fun installCommit(): String {
    return "Success\n"
  }
}
