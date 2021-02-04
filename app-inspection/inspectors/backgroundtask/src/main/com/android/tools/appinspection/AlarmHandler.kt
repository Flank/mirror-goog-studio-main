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
import androidx.inspection.InspectorEnvironment
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.*
import java.util.concurrent.ConcurrentHashMap

internal class AlarmHandler(
    connection: Connection,
    environment: InspectorEnvironment
) {

    /** Data structure for {@link OnAlarmListener} parameters. */
    data class ListenerParams(val id: Long, val tag: String)

    /** Data structure for PendingIntent Alarm parameters. */
    data class PendingIntentParams(val id: Long, val isRepeating: Boolean)

    private val operationMap = ConcurrentHashMap<PendingIntent, PendingIntentParams>()
    private val listenerMap = ConcurrentHashMap<OnAlarmListener, ListenerParams>()

    init {
        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "setImpl" +
                    "(IJJJILandroid/app/PendingIntent;" +
                    "Landroid/app/AlarmManager\$OnAlarmListener;Ljava/lang/String;" +
                    "Landroid/os/Handler;Landroid/os/WorkSource;" +
                    "Landroid/app/AlarmManager\$AlarmClockInfo;)V"
        ) { _, args ->
            val type = args[0] as Int
            if (type != AlarmManager.RTC_WAKEUP
                && type != AlarmManager.ELAPSED_REALTIME_WAKEUP
            ) {
                // Only instrument wakeup alarms.
                return@registerEntryHook
            }

            var taskId = -1L
            val alarmSet = AlarmSet.newBuilder().apply {
                this.type = AlarmSet.Type.forNumber(type)
                triggerMs = args[1] as Long
                windowMs = args[2] as Long
                intervalMs = args[3] as Long
                val operation = args[5] as PendingIntent?
                val listener = args[6] as OnAlarmListener?
                val listenerTag = args[7] as String?
                when {
                    operation != null -> {
                        operationMap.putIfAbsent(
                            operation,
                            PendingIntentParams(BackgroundTaskUtil.nextId(), intervalMs != 0L)
                        )
                        taskId = operationMap[operation]!!.id
                        this.operation = BackgroundTaskInspectorProtocol.PendingIntent.newBuilder()
                            .setCreatorPackage(operation.creatorPackage)
                            .setCreatorUid(operation.creatorUid)
                            .build()
                    }
                    listener != null -> {
                        listenerMap.putIfAbsent(
                            listener,
                            ListenerParams(BackgroundTaskUtil.nextId(), listenerTag ?: "")
                        )
                        taskId = listenerMap[listener]!!.id
                        this.listener = AlarmListener.newBuilder()
                            .setTag(listenerTag)
                            .build()
                    }
                    else -> throw IllegalStateException("Invalid alarm: neither operation or listener is set.")
                }
            }.build()
            val backgroundTaskEvent = BackgroundTaskEvent.newBuilder()
                .setTaskId(taskId)
                .setAlarmSet(alarmSet)
                .build()
            val event = Event.newBuilder().setBackgroundTaskEvent(backgroundTaskEvent).build()
            connection.sendEvent(event.toByteArray())
        }

        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "cancel(Landroid/app/PendingIntent;)V"
        ) { _, args ->
            val operation = args[0] as PendingIntent
            val taskId = operationMap[operation]?.id ?: return@registerEntryHook
            val backgroundTaskEvent = BackgroundTaskEvent.newBuilder()
                .setTaskId(taskId)
                .setAlarmCancelled(AlarmCancelled.getDefaultInstance())
                .build()
            val event = Event.newBuilder().setBackgroundTaskEvent(backgroundTaskEvent).build()
            connection.sendEvent(event.toByteArray())
        }

        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "cancel(Landroid/app/AlarmManager\$OnAlarmListener;)V"
        ) { _, args ->
            val listener = args[0] as OnAlarmListener
            val taskId = listenerMap[listener]?.id ?: return@registerEntryHook
            val backgroundTaskEvent = BackgroundTaskEvent.newBuilder()
                .setTaskId(taskId)
                .setAlarmCancelled(AlarmCancelled.getDefaultInstance())
                .build()
            val event = Event.newBuilder().setBackgroundTaskEvent(backgroundTaskEvent).build()
            connection.sendEvent(event.toByteArray())
        }

        environment.artTooling().registerEntryHook(
            AlarmManager.OnAlarmListener::class.java,
            "onAlarm()V"
        ) { _, args ->
            val operation = args[0] as PendingIntent
            val taskId = operationMap[operation]?.id ?: return@registerEntryHook
            val backgroundTaskEvent = BackgroundTaskEvent.newBuilder()
                .setTaskId(taskId)
                .setAlarmFired(AlarmFired.getDefaultInstance())
                .build()
            val event = Event.newBuilder().setBackgroundTaskEvent(backgroundTaskEvent).build()
            connection.sendEvent(event.toByteArray())
        }

        environment.artTooling().registerEntryHook(
            AlarmManager.OnAlarmListener::class.java,
            "onAlarm()V"
        ) { _, args ->
            val operation = args[0] as PendingIntent
            val taskId = operationMap[operation]?.id ?: return@registerEntryHook
            val backgroundTaskEvent = BackgroundTaskEvent.newBuilder()
                .setTaskId(taskId)
                .setAlarmFired(AlarmFired.getDefaultInstance())
                .build()
            val event = Event.newBuilder().setBackgroundTaskEvent(backgroundTaskEvent).build()
            connection.sendEvent(event.toByteArray())
        }
    }
}
