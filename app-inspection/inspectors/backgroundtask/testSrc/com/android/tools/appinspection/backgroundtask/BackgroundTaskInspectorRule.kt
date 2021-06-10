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

package com.android.tools.appinspection.backgroundtask

import androidx.inspection.Inspector
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Command
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.TrackBackgroundTaskCommand
import com.android.tools.appinspection.BackgroundTaskInspector
import com.android.tools.appinspection.BackgroundTaskInspectorFactory
import com.android.tools.appinspection.BackgroundTaskUtil
import org.junit.rules.ExternalResource
import java.util.concurrent.Executor

class BackgroundTaskInspectorRule : ExternalResource() {

    lateinit var connection: FakeConnection
    lateinit var environment: FakeEnvironment
    lateinit var inspector: BackgroundTaskInspector

    override fun before() {
        connection = FakeConnection()
        environment = FakeEnvironment()
        inspector = BackgroundTaskInspectorFactory().createInspector(connection, environment)
        inspector.onReceiveCommand(
            Command.newBuilder()
                .setTrackBackgroundTask(TrackBackgroundTaskCommand.getDefaultInstance())
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
        BackgroundTaskUtil.atomicLong.set(0)
    }
}
