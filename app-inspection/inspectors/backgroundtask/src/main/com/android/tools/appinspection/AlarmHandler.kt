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

import android.app.AlarmManager
import android.app.AlarmManager.OnAlarmListener
import android.app.PendingIntent
import androidx.inspection.Connection
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import com.android.tools.appinspection.BackgroundTaskUtil.sendBackgroundTaskEvent
import com.android.tools.appinspection.common.getStackTrace
import java.util.concurrent.ConcurrentHashMap

/**
 * A handler class that adds necessary hooks to track alarm related events.
 */
interface AlarmHandler {

    fun onAlarmSet(
        type: Int,
        triggerMs: Long,
        windowMs: Long,
        intervalMs: Long,
        operation: PendingIntent?,
        listener: OnAlarmListener?,
        listenerTag: String?
    )

    fun onAlarmCancelled(operation: PendingIntent)
    fun onAlarmCancelled(listener: OnAlarmListener)
    fun onAlarmFired(listener: OnAlarmListener)
    fun onAlarmFired(operation: PendingIntent)
}

class AlarmHandlerImpl(private val connection: Connection) : AlarmHandler {

    private val operationIdMap = ConcurrentHashMap<PendingIntent, Long>()
    private val listenerIdMap = ConcurrentHashMap<OnAlarmListener, Long>()

    override fun onAlarmSet(
        type: Int,
        triggerMs: Long,
        windowMs: Long,
        intervalMs: Long,
        operation: PendingIntent?,
        listener: OnAlarmListener?,
        listenerTag: String?
    ) {
        if (type != AlarmManager.RTC_WAKEUP
            && type != AlarmManager.ELAPSED_REALTIME_WAKEUP
        ) {
            // Only instrument wakeup alarms.
            return
        }
        var taskId = -1L
        val builder = BackgroundTaskInspectorProtocol.AlarmSet.newBuilder().apply {
            this.type =
                if (type == AlarmManager.RTC_WAKEUP)
                    BackgroundTaskInspectorProtocol.AlarmSet.Type.RTC_WAKEUP
                else BackgroundTaskInspectorProtocol.AlarmSet.Type.ELAPSED_REALTIME_WAKEUP
            this.triggerMs = triggerMs
            this.windowMs = windowMs
            this.intervalMs = intervalMs
            when {
                operation != null -> {
                    taskId =
                        operationIdMap.getOrPut(operation) { BackgroundTaskUtil.nextId() }
                    this.operation =
                        BackgroundTaskInspectorProtocol.PendingIntent.newBuilder()
                            .setCreatorPackage(operation.creatorPackage)
                            .setCreatorUid(operation.creatorUid)
                            .build()
                }
                listener != null -> {
                    taskId =
                        listenerIdMap.getOrPut(listener) { BackgroundTaskUtil.nextId() }
                    this.listener = BackgroundTaskInspectorProtocol.AlarmListener.newBuilder()
                        .setTag(listenerTag)
                        .build()
                }
                else -> throw IllegalStateException("Invalid alarm: neither operation or listener is set.")
            }
        }

        connection.sendBackgroundTaskEvent(taskId) {
            stacktrace = getStackTrace(1)
            alarmSet = builder.build()
        }
    }

    override fun onAlarmCancelled(operation: PendingIntent) {
        val taskId = operationIdMap[operation] ?: return
        connection.sendBackgroundTaskEvent(taskId) {
            stacktrace = getStackTrace(1)
            alarmCancelled = BackgroundTaskInspectorProtocol.AlarmCancelled.getDefaultInstance()
        }
    }

    override fun onAlarmCancelled(listener: OnAlarmListener) {
        val taskId = listenerIdMap[listener] ?: return
        connection.sendBackgroundTaskEvent(taskId) {
            stacktrace = getStackTrace(1)
            alarmCancelled = BackgroundTaskInspectorProtocol.AlarmCancelled.getDefaultInstance()
        }
    }

    override fun onAlarmFired(listener: OnAlarmListener) {
        val taskId = listenerIdMap[listener] ?: return
        connection.sendBackgroundTaskEvent(taskId) {
            alarmFired = BackgroundTaskInspectorProtocol.AlarmFired.getDefaultInstance()
        }
    }

    override fun onAlarmFired(operation: PendingIntent) {
        val taskId = operationIdMap[operation] ?: return
        connection.sendBackgroundTaskEvent(taskId) {
            alarmFired = BackgroundTaskInspectorProtocol.AlarmFired.getDefaultInstance()
        }
    }
}
