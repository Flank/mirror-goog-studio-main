/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.fakeadbserver.shellv2commandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket

/**
 * A specialized version of shell handlers that assumes the command are of the form "exe arg1 arg2".
 * For more complex handlers extend [ShellV2Handler] directly.
 */
abstract class SimpleShellV2Handler(private val executable: String) : ShellV2Handler() {

    override fun accept(
        server: FakeAdbServer,
        socket: Socket,
        device: DeviceState,
        command: String,
        args: String
    ): Boolean {
        val split = args.split(" ", limit = 2).toTypedArray()
        if (this.command == command && executable == split[0]) {
            val protocol = ShellV2Protocol(socket)
            execute(server, protocol, device, if (split.size > 1) split[1] else "")
            return true
        }
        return false
    }

    /**
     * This is the main execution method of the command.
     *
     * @param fakeAdbServer Fake ADB Server itself.
     * @param protocol Shell V2 protocol handler for this connection.
     * @param device Target device for the command, if any.
     * @param args Arguments for the executable, if any.
     */
    abstract fun execute(
        fakeAdbServer: FakeAdbServer,
        protocol: ShellV2Protocol,
        device: DeviceState,
        args: String?
    )
}
