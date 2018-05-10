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
import java.net.Socket
import java.nio.charset.StandardCharsets

class SyncCommandHandler : DeviceCommandHandler() {
  companion object {
    const val COMMAND = "sync"
  }

  override fun invoke(fakeAdbServer: FakeAdbServer, responseSocket: Socket, device: DeviceState, args: String): Boolean {
    try {
      val output = responseSocket.getOutputStream()
      // Sync used for transferring files. Newly created emulator devices should not fail to handle this command.
      CommandHandler.writeOkay(output)

      // TODO the following is a hack. See http://b/79271028
      output.write("OKAY".toByteArray(StandardCharsets.UTF_8).copyOf(8))
      // Drain input stream. This data comes from files
      val input = responseSocket.getInputStream()
      val buffer= ByteArray(1024)
      var bytesRead = 0
      while (bytesRead >= 0) {
        bytesRead = input.read(buffer)
      }
    }
    catch (ignored: IOException) {
      // Unable to respond to the client, and we can't do anything about it. Swallow the exception and continue on
    }
    return false
  }
}