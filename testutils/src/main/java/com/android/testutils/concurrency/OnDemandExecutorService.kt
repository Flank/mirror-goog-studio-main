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
package com.android.testutils.concurrency

import java.util.LinkedList
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * [ExecutorService] that queues the tasks and will only execute them when [run] or [runAll]
 * are called.
 *
 * This class is thread-safe.
 */
class OnDemandExecutorService : AbstractExecutorService() {
    private var isTerminated = false
    private val taskQueue = LinkedList<Runnable>()
    val queueSize: Int get() = taskQueue.size

    @Synchronized
    override fun isTerminated(): Boolean = this.isTerminated

    @Synchronized
    override fun execute(command: Runnable) {
        if (!isTerminated) taskQueue.offer(command)
    }

    @Synchronized
    override fun shutdownNow(): MutableList<Runnable> {
        isTerminated = true
        return taskQueue
    }

    override fun shutdown() {
        shutdownNow()
    }

    @Synchronized
    override fun isShutdown(): Boolean = isTerminated

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        shutdown()
        return true
    }

    /**
     * Runs [tasks] from the queue
     */
    @Synchronized
    fun run(tasks: Int) {
        repeat(tasks) {
            taskQueue.remove().run()
        }
    }

    /**
     * Runs all the pending tasks
     */
    @Synchronized
    fun runAll(): Int {
        val numberOfTasksToRun = taskQueue.size
        run(numberOfTasksToRun)
        return numberOfTasksToRun
    }
}