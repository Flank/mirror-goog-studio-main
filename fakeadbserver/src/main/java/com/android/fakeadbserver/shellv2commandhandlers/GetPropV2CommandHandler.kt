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
import com.android.fakeadbserver.ShellV2Protocol
import com.google.common.base.Charsets

/**
 * A [SimpleShellV2Handler] that outputs a hard-coded list of lines that follows the format
 * of device properties.
 */
class GetPropV2CommandHandler : SimpleShellV2Handler("getprop") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        protocol: ShellV2Protocol,
        device: DeviceState,
        args: String?
    ) {
        protocol.writeOkay()

        val buf = StringBuilder()
        buf.append("# This is some build info\n")
        buf.append("# This is more build info\n")
        buf.append("\n")
        for (entry in device.properties) {
            buf.append("[${entry.key}]: [${entry.value}]\n")
        }
        protocol.writeStdout(buf.toString().toByteArray(Charsets.UTF_8))
        protocol.writeExitCode(0)
    }
}
