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
package com.android.sdklib.deviceprovisioner

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.channels.ServerSocketChannel

class FakeEmulatorConsole(val avdName: String, val avdPath: String) {
  val server = ServerSocketChannel.open().bind(null)
  val port = server.socket().localPort
  var socket: Socket? = null

  fun start() {
    Thread(this::run, "FakeEmulator").start()
  }

  fun run() {
    try {
      val socket = server.accept().socket()
      this.socket = socket
      val input = BufferedReader(InputStreamReader(socket.getInputStream()))
      val output = PrintWriter(socket.getOutputStream())
      output.write("Hello\r\nOK\r\n")
      output.flush()
      while (true) {
        val line = input.readLine() ?: return
        when (line.trim()) {
          "avd name" -> output.write(avdName + "\r\nOK\r\n")
          "avd path" -> output.write(avdPath + "\r\nOK\r\n")
          "kill" -> {
            output.write("OK\r\n")
            output.flush()
            close()
            return
          }
          else -> output.write("KO: unsupported command: $line\r\n")
        }
        output.flush()
      }
    } catch (e: IOException) {
      // Ignore socket closing
    }
  }

  fun close() {
    server.close()
    socket?.close()
  }
}
