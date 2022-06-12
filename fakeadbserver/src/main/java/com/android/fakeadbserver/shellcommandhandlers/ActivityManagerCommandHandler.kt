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
import com.android.fakeadbserver.services.ExecServiceOutput
import com.android.fakeadbserver.services.ServiceManager
import java.net.Socket

class ActivityManagerCommandHandler : SimpleShellHandler("am") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState,
        args: String?
    ) {
        val output = responseSocket.getOutputStream()

        if (args == null) {
            CommandHandler.writeFail(output)
            return
        }

        CommandHandler.writeOkay(output)

        val serviceOutput = ExecServiceOutput(responseSocket)

        // Create a service request
        val params = mutableListOf(ServiceManager.ACTIVITY_MANAGER_SERVICE_NAME)
        params.addAll(args.split(" "))
        device.serviceManager.processCommand(params, serviceOutput)
    }
}
