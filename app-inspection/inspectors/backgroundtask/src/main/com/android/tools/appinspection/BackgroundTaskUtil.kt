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
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Event
import java.util.concurrent.atomic.AtomicLong

object BackgroundTaskUtil {

    private val atomicLong: AtomicLong = AtomicLong()

    /** Generates a unique energy event ID.  */
    fun nextId(): Long {
        return atomicLong.incrementAndGet()
    }

    fun Connection.sendBackgroundTaskEvent(
        taskId: Long,
        eventCompleter: (BackgroundTaskEvent.Builder) -> BackgroundTaskEvent.Builder
    ) {
        val initialBackgroundTaskEventBuilder = BackgroundTaskEvent.newBuilder()
            .setTaskId(taskId)
        val backgroundTaskEvent = eventCompleter(initialBackgroundTaskEventBuilder).build()
        val eventBuilder = Event.newBuilder().apply {
            this.backgroundTaskEvent = backgroundTaskEvent
            timestamp = System.nanoTime()
        }
        sendEvent(eventBuilder.build().toByteArray())
    }
}
