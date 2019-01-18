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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.io.IOException
import java.net.Socket

class AbbCommandHandler : HostCommandHandler() {
  companion object {
    const val COMMAND = "abb"
  }

  override fun invoke(fakeAdbServer: FakeAdbServer, respSocket: Socket, state: DeviceState?, args: String): Boolean {
    try {
      val output = respSocket.getOutputStream()

      CommandHandler.writeOkay(output)

      val response: String = when {
        args.startsWith("package install-create") -> installMultiple()
        args.startsWith("package install-commit") -> installCommit()
        else -> ""
      }

      CommandHandler.writeString(output, response)
    } catch(ignored: IOException) {
    }

    return false
  }

  /**
   * Handler for commands that look like:
   *
   *    adb abb package install-create -r -t --ephemeral -S 1298948
   */
  private fun installMultiple(): String {
    return "Success: created install session [1234]"
  }

  /**
   * handler for commands that look like:
   *
   *    adb abb package install-commit 538681231
   */
  private fun installCommit(): String {
    return "Success\n"
  }
}
