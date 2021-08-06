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

import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Event
import java.util.concurrent.atomic.AtomicLong

object BackgroundTaskUtil {

    @VisibleForTesting
    val atomicLong: AtomicLong = AtomicLong()

    /** Generates a unique energy event ID.  */
    fun nextId(): Long {
        return atomicLong.incrementAndGet()
    }

    /**
     * Send a background task event from connection.
     *
     * @param taskId a unique id across different background events.
     * @param setMetaData a unit function that adds information to the event builder.
     */
    fun Connection.sendBackgroundTaskEvent(
        taskId: Long,
        setMetaData: BackgroundTaskEvent.Builder.() -> Unit
    ) {
        val eventBuilder = Event.newBuilder().apply {
            this.backgroundTaskEventBuilder.apply {
                this.taskId = taskId
                setMetaData()
            }
            timestamp = System.currentTimeMillis()
        }
        sendEvent(eventBuilder.build().toByteArray())
    }
}
