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
import java.io.IOException
import java.net.Socket

class ActivityManagerCommandHandler(
  private val processStarter: ProcessStarter
) : ShellCommandHandler() {

  companion object {
    const val COMMAND = "am"
  }

  override fun invoke(fakeAdbServer: FakeAdbServer, responseSocket: Socket, device: DeviceState, args: String?): Boolean {
    try {
      val output = responseSocket.getOutputStream()

      if (args == null) {
        CommandHandler.writeFail(output)
        return false
      }

      CommandHandler.writeOkay(output)

      val response: String = when {
        args.startsWith("start") -> processStarter.startProcess(device)
        else -> ""
      }

      CommandHandler.writeString(output, response)
    }
    catch (ignored: IOException) {
      // Unable to write to socket. Can't communicate anything with client. Just swallow
      // the exception and move on
    }

    return false
  }

  interface ProcessStarter {
    fun startProcess(deviceState: DeviceState): String
  }

}