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

package com.android.tools.agent.appinspection.testutils.inspection

import android.os.Handler
import android.os.Looper
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorExecutors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A simple implementation of the [InspectorEnvironment] class, which as a side effect spins up its
 * own inspector thread.
 *
 * You must call [shutdown] at the end of your test, to verify the primary thread is stopped.
 *
 * See also: [InspectorRule]
 */
class TestInspectorEnvironment() : InspectorEnvironment {

    private val looperFuture = CompletableFuture<Looper>()
    private val inspectorThread = Thread(
        {
            Looper.prepare()
            looperFuture.complete(Looper.myLooper())
            Looper.loop()
        }, "Inspector-Primary"
    ).also { it.start() }

    private val executors = object : InspectorExecutors {
        private val handler = Handler(looperFuture.get())
        private val primaryExecutor = Executor { command -> handler.post(command) }

        override fun handler() = handler
        override fun primary() = primaryExecutor
        override fun io() = throw NotImplementedError()
    }

    override fun executors(): InspectorExecutors = executors
    override fun artTooling() = throw NotImplementedError()

    fun shutdown() {
        looperFuture.get().quitSafely()
        inspectorThread.join()
    }
}
