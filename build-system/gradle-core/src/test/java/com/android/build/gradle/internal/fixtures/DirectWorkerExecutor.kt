/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import com.android.ide.common.workers.WorkerExecutorFacade
import java.io.Serializable

/** An implementation of [WorkerExecutorFacade] that executes runnables directly */
class DirectWorkerExecutor : WorkerExecutorFacade {

    override fun submit(
        actionClass: Class<out Runnable>,
        parameter: Serializable
    ) {
        val constructor = actionClass.getDeclaredConstructor(parameter.javaClass)
        val isConstructorAccessible = constructor.isAccessible
        constructor.isAccessible = true
        val action = constructor.newInstance(parameter)
        action.run()
        constructor.isAccessible = isConstructorAccessible
    }

    override fun await() {
        // do nothing.
    }

    override fun close() {
        // do nothing.
    }
}