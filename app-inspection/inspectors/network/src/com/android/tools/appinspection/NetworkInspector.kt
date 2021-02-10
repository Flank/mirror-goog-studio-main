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

package com.android.tools.appinspection

import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import studio.network.inspection.NetworkInspectorProtocol
import java.util.concurrent.CancellationException

class NetworkInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) : Inspector(connection) {

    private val supervisorJob = SupervisorJob()
    private val scope =
        CoroutineScope(supervisorJob + environment.executors().primary().asCoroutineDispatcher())

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = NetworkInspectorProtocol.Command.parseFrom(data)
        callback.reply(
            NetworkInspectorProtocol.Response.newBuilder()
                .setTestResponse(
                    NetworkInspectorProtocol.TestResponse.newBuilder()
                        .setCmdId(command.testCommand.cmdId)
                        .setResponse("Received: ${command.testCommand.message}")
                        .build()
                )
                .build()
                .toByteArray()
        )
    }

    override fun onDispose() {
        if (!supervisorJob.isCancelled) {
            supervisorJob.cancel(CancellationException("Network Inspector has been disposed."))
        }
    }
}
