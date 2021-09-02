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

import android.app.Activity
import android.app.ActivityThread
import android.app.AlarmManager
import android.app.Instrumentation
import android.app.IntentService
import android.app.JobSchedulerImpl
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Command
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.Response
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.TrackBackgroundTaskResponse

private const val BACKGROUND_INSPECTION_ID = "backgroundtask.inspection"

class BackgroundTaskInspectorFactory : InspectorFactory<BackgroundTaskInspector>(
    BACKGROUND_INSPECTION_ID
) {

    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment
    ) = BackgroundTaskInspector(connection, environment)
}

class BackgroundTaskInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) :
    Inspector(connection) {

    @VisibleForTesting
    lateinit var alarmHandler: AlarmHandler

    @VisibleForTesting
    lateinit var pendingIntentHandler: PendingIntentHandler

    @VisibleForTesting
    lateinit var wakeLockHandler: WakeLockHandler

    @VisibleForTesting
    lateinit var jobHandler: JobHandler

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = Command.parseFrom(data)
        when (command.specializedCase) {
            Command.SpecializedCase.TRACK_BACKGROUND_TASK -> {
                startBackgroundTaskHandlers()
                callback.reply(
                    Response.newBuilder().setTrackBackgroundTask(
                        TrackBackgroundTaskResponse.getDefaultInstance()
                    ).build().toByteArray()
                )
            }
            else -> throw IllegalStateException(
                "Unexpected view inspector command case: ${command.specializedCase}"
            )
        }
    }

    override fun onDispose() {
    }

    private fun startBackgroundTaskHandlers() {
        registerAlarmHooks()
        registerPendingIntentHooks()
        registerWakeLockHooks()
        registerJobHooks()
    }

    private fun registerAlarmHooks() {
        alarmHandler = AlarmHandlerImpl(connection)
        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "setImpl" +
                    "(IJJJILandroid/app/PendingIntent;" +
                    "Landroid/app/AlarmManager\$OnAlarmListener;Ljava/lang/String;" +
                    "Landroid/os/Handler;Landroid/os/WorkSource;" +
                    "Landroid/app/AlarmManager\$AlarmClockInfo;)V"
        ) { _, args ->
            alarmHandler.onAlarmSet(
                type = args[0] as Int,
                triggerMs = args[1] as Long,
                windowMs = args[2] as Long,
                intervalMs = args[3] as Long,
                operation = args[5] as PendingIntent?,
                listener = args[6] as AlarmManager.OnAlarmListener?,
                listenerTag = args[7] as String?
            )
        }
        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "cancel(Landroid/app/PendingIntent;)V"
        ) { _, args ->
            alarmHandler.onAlarmCancelled((args[0] as? PendingIntent) ?: return@registerEntryHook)
        }
        environment.artTooling().registerEntryHook(
            AlarmManager::class.java,
            "cancel(Landroid/app/AlarmManager\$OnAlarmListener;)V"
        ) { _, args ->
            alarmHandler.onAlarmCancelled(
                (args[0] as? AlarmManager.OnAlarmListener) ?: return@registerEntryHook
            )
        }
        environment.artTooling().registerEntryHook(
            AlarmManager.OnAlarmListener::class.java,
            "onAlarm()V"
        ) { listener, _ ->
            alarmHandler.onAlarmFired(listener as AlarmManager.OnAlarmListener)
        }
    }

    private fun registerPendingIntentHooks() {
        pendingIntentHandler = PendingIntentHandlerImpl(alarmHandler)
        listOf(
            GET_ACTIVITY_METHOD_NAME,
            GET_SERVICES_METHOD_NAME,
            GET_BROADCAST_METHOD_NAME
        ).forEach { methodName ->
            environment.artTooling().registerEntryHook(
                PendingIntent::class.java,
                methodName
            ) { _, args ->
                pendingIntentHandler.onIntentCapturedEntry(
                    (args[2] as? Intent) ?: return@registerEntryHook
                )
            }
            environment.artTooling().registerExitHook(
                PendingIntent::class.java,
                methodName
            ) { pendingIntent: PendingIntent? ->
                pendingIntent?.let {
                    pendingIntentHandler.onIntentCapturedExit(it)
                }
            }
        }

        listOf(
            CALL_ACTIVITY_ON_CREATE_METHOD_NAME,
            CALL_ACTIVITY_ON_CREATE_PERSISTABLE_BUNDLE_METHOD_NAME
        ).forEach { methodName ->
            environment.artTooling().registerEntryHook(
                Instrumentation::class.java,
                methodName
            ) { _, args ->
                pendingIntentHandler.onIntentReceived(
                    (args[0] as? Activity)?.intent ?: return@registerEntryHook
                )
            }
        }

        environment.artTooling().registerEntryHook(
            IntentService::class.java,
            ON_START_COMMAND_METHOD_NAME
        ) { _, args ->
            pendingIntentHandler.onIntentReceived((args[0] as? Intent) ?: return@registerEntryHook)
        }

        environment.artTooling().registerEntryHook(
            ActivityThread::class.java,
            HANDLE_RECEIVER_METHOD_NAME
        ) { _, args ->
            pendingIntentHandler.onReceiverDataCreated(args[0] ?: return@registerEntryHook)
        }

        environment.artTooling().registerEntryHook(
            BroadcastReceiver::class.java,
            SET_PENDING_RESULT_METHOD_NAME
        ) { _, args ->
            pendingIntentHandler.onReceiverDataResult(args[0] ?: return@registerEntryHook)
        }
    }

    private fun registerWakeLockHooks() {
        wakeLockHandler = WakeLockHandlerImpl(connection)

        environment.artTooling().registerEntryHook(
            PowerManager::class.java,
            "newWakeLock" +
                    "(ILjava/lang/String;)Landroid/os/PowerManager\$WakeLock;"
        ) { _, args ->
            wakeLockHandler.onNewWakeLockEntry(args[0] as Int, (args[1] as String?) ?: "")
        }
        environment.artTooling().registerExitHook<WakeLock>(
            PowerManager::class.java,
            "newWakeLock" +
                    "(ILjava/lang/String;)Landroid/os/PowerManager\$WakeLock;"
        ) { wakeLock ->
            wakeLockHandler.onNewWakeLockExit(wakeLock)
        }

        environment.artTooling().registerEntryHook(
            WakeLock::class.java,
            "acquire()V"
        ) { wakeLock, _ ->
            wakeLockHandler.onWakeLockAcquired(wakeLock as WakeLock, 0)
        }

        environment.artTooling().registerEntryHook(
            WakeLock::class.java,
            "acquire(J)V"
        ) { wakeLock, args ->
            wakeLockHandler.onWakeLockAcquired(wakeLock as WakeLock, args[0] as Long)
        }

        environment.artTooling().registerEntryHook(
            WakeLock::class.java,
            "release(I)V"
        ) { wakeLock, args ->
            wakeLockHandler.onWakeLockReleasedEntry(wakeLock as WakeLock, args[0] as Int)
        }


        environment.artTooling().registerExitHook<Void>(
            WakeLock::class.java,
            "release(I)V",
        ) {
            wakeLockHandler.onWakeLockReleasedExit()
            it
        }
    }

    private fun registerJobHooks() {
        jobHandler = JobHandlerImpl(connection)
        environment.artTooling().registerEntryHook(
            JobSchedulerImpl::class.java,
            "schedule(Landroid/app/job/JobInfo;)I"
        ) { _, args ->
            jobHandler.onScheduleJobEntry((args[0] as JobInfo?) ?: return@registerEntryHook)
        }

        environment.artTooling().registerExitHook<Int>(
            JobSchedulerImpl::class.java,
            "schedule(Landroid/app/job/JobInfo;)I"
        ) { scheduleResult ->
            jobHandler.onScheduleJobExit(scheduleResult)
        }

        val jobHandlerClass = Class.forName("android.app.job.JobServiceEngine\$JobHandler")
        environment.artTooling().registerEntryHook(
            jobHandlerClass,
            "ackStartMessage(Landroid/app/job/JobParameters;Z)V"
        ) { _, args ->
            jobHandler.wrapOnStartJob(
                params = (args[0] as? JobParameters) ?: return@registerEntryHook,
                workOngoing = args[1] as Boolean
            )
        }

        environment.artTooling().registerEntryHook(
            jobHandlerClass,
            "ackStopMessage(Landroid/app/job/JobParameters;Z)V"
        ) { _, args ->
            jobHandler.wrapOnStopJob(
                params = (args[0] as? JobParameters) ?: return@registerEntryHook,
                reschedule = args[1] as Boolean
            )
        }

        environment.artTooling().registerEntryHook(
            JobService::class.java,
            "jobFinished(Landroid/app/job/JobParameters;Z)V"
        ) { _, args ->
            jobHandler.wrapJobFinished(
                params = (args[0] as? JobParameters) ?: return@registerEntryHook,
                wantsReschedule = args[1] as Boolean
            )
        }
    }
}
