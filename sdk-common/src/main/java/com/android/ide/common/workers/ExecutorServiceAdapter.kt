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

package com.android.ide.common.workers

import java.io.Serializable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Implementation of [WorkerExecutorFacade] using a plain JDK [ExecutorService]
 */
open class ExecutorServiceAdapter(
    /**
     * Instantiate an adapter using the passed [ExecutorService]
     */
    val executor: ExecutorService) : WorkerExecutorFacade {

    private val futures = mutableListOf<Future<*>>()

    override fun submit(actionClass: Class<out Runnable>, parameter: Serializable) {
        val submission = executor.submit {
            val constructor = actionClass.getDeclaredConstructor(parameter.javaClass)
            constructor.isAccessible = true
            val action = constructor.newInstance(parameter)
            action.run()
        }
        synchronized(this) {
            futures.add(submission)
        }
    }

    override fun await() {
        val currentTasks = mutableListOf<Future<*>>()
        synchronized(this) {
            currentTasks.addAll(futures)
            futures.clear()
        }
        val exceptions = ArrayList<Throwable>()
        currentTasks.forEach {
            try {
                it.get()
            } catch (e: ExecutionException) {
                exceptions.add(e)
            }
        }
        if (!exceptions.isEmpty()) {
            throw WorkerExecutorException(exceptions)
        }
    }

    // We need to call await on closing because Gradle is not aware of any java workers spawned by
    // a task, so we should wait till everything is finished.
    override fun close() {
        await()
    }
}