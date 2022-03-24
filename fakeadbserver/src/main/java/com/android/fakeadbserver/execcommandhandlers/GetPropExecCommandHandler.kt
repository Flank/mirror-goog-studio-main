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
package com.android.fakeadbserver.execcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.google.common.base.Charsets
import java.net.Socket

class GetPropExecCommandHandler : SimpleExecHandler("getprop") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState,
        args: String?
    ) {
        val stream = responseSocket.getOutputStream()
        writeOkay(stream) // Send ok first.
        val builder = """
            # This is some build info
            # This is more build info

            [ro.product.manufacturer]: [${device.manufacturer}]
            [ro.product.model]: [${device.model}]
            [ro.build.version.release]: [${device.buildVersionRelease}]
            [ro.build.version.sdk]: [${device.buildVersionSdk}]

            """.trimIndent()
        stream.write(builder.toByteArray(Charsets.UTF_8))
    }
}
