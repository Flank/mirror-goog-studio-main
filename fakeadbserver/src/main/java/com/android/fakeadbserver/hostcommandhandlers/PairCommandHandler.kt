/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket
import java.util.UUID

/** Simulates the behavior of the `pair` command  */
class PairCommandHandler : HostCommandHandler() {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        val separatorIndex = args.indexOf(":")
        if (separatorIndex < 0) {
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/sockets.cpp;l=809;bpv=1;bpt=1
            writeFailResponse(responseSocket.getOutputStream(), "unknown host service")
        }
        val password = args.substring(0, separatorIndex)
        val deviceAddress = args.substring(separatorIndex + 1)
        val mdnsServices = fakeAdbServer.mdnsServicesCopy.get()

        val service = mdnsServices.firstOrNull { service ->
            "${service.deviceAddress.hostString}:${service.deviceAddress.port}" == deviceAddress
        }
        if (service == null) {
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/adb_wifi.cpp;l=233
            writeOkayResponse(
                responseSocket.getOutputStream(),
                "Failed: Unable to start pairing client."
            )
        } else {
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/adb_wifi.cpp;l=249
            writeOkayResponse(
                responseSocket.getOutputStream(),
                "Successfully paired to $deviceAddress [guid=${UUID.randomUUID()}]\n"
            )
        }
        return false
    }

    companion object {

        const val COMMAND = "pair"
    }
}
