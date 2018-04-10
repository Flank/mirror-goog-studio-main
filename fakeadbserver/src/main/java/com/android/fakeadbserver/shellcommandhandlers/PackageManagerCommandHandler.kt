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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.google.common.base.Charsets
import java.io.IOException
import java.net.Socket
import java.util.regex.Pattern

class PackageManagerCommandHandler : ShellCommandHandler() {
  companion object {
    const val COMMAND = "pm"
  }

  override fun invoke(fakeAdbServer: FakeAdbServer, respSocket: Socket, state: DeviceState, args: String?): Boolean {
    try {
      val output = respSocket.getOutputStream()

      if (args == null) {
        CommandHandler.writeFail(output)
        return false
      }

      CommandHandler.writeOkay(output)

      // check what value args is. Get the first argument from args to see what sort of subcommand it is
      val response: String = when {
        args == "list users" -> listUsers()
        args.startsWith("path ") -> pathToApp(args)
        else -> ""
      }

      output.write(response.toByteArray(Charsets.UTF_8))
    } catch(ignored: IOException) {
    }
    return false
  }

  /**
   * `pm list users` subcommand, as used in `adb shell pm list users`
   */
  private fun listUsers(): String {
    return "Users:\n" +
         "        UserInfo{0:Owner:13} running\n"
  }

  /**
   * `pm path <app>` subcommand, e.g. `adb shell pm path com.google.android.simple`
   */
  private fun pathToApp(args: String): String {
    val pathArgs = args.split(Pattern.compile(" "), 2)
    if (pathArgs.size < 2) {
      return "Error: no package specified"
    }

    return "package:/data/app/${pathArgs.last()}.apk"
  }
}