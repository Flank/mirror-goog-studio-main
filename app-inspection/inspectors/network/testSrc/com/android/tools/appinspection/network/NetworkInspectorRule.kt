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

package com.android.tools.appinspection.network

import androidx.inspection.Inspector
import com.android.tools.appinspection.network.utils.ConnectionIdGenerator
import org.junit.rules.ExternalResource
import studio.network.inspection.NetworkInspectorProtocol
import java.util.concurrent.Executor

class NetworkInspectorRule : ExternalResource() {

    lateinit var connection: FakeConnection
    lateinit var environment: FakeEnvironment
    lateinit var inspector: NetworkInspector

    override fun before() {
        connection = FakeConnection()
        environment = FakeEnvironment()
        inspector = NetworkInspectorFactory().createInspector(connection, environment)
        inspector.onReceiveCommand(
            NetworkInspectorProtocol.Command.newBuilder()
                .setStartInspectionCommand(
                    NetworkInspectorProtocol.StartInspectionCommand.getDefaultInstance()
                )
                .build()
                .toByteArray(),
            object : Inspector.CommandCallback {
                override fun reply(response: ByteArray) {
                }

                override fun addCancellationListener(executor: Executor, runnable: Runnable) {
                }
            }
        )
    }

    override fun after() {
        inspector.onDispose()
        ConnectionIdGenerator.id.set(0)
    }
}
