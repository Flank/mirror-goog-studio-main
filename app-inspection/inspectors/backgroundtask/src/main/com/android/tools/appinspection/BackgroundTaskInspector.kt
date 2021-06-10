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

import android.app.JobSchedulerImpl
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
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

    private lateinit var alarmHandler: AlarmHandler
    private lateinit var pendingIntentHandler: PendingIntentHandler
    private lateinit var wakeLockHandler: WakeLockHandler

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
        alarmHandler = AlarmHandler(connection, environment)
        pendingIntentHandler = PendingIntentHandler(environment, alarmHandler)
        wakeLockHandler = WakeLockHandler(connection, environment)
        registerJobHooks()
    }

    private fun registerJobHooks() {
        jobHandler = JobHandlerImpl(connection)
        environment.artTooling().registerEntryHook(
            JobSchedulerImpl::class.java,
            "schedule(Landroid/app/job/JobInfo;)I"
        ) { _, args ->
            jobHandler.onScheduleJobEntry(job = args[0] as JobInfo)
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
                params = args[0] as JobParameters,
                workOngoing = args[1] as Boolean
            )
        }

        environment.artTooling().registerEntryHook(
            jobHandlerClass,
            "ackStopMessage(Landroid/app/job/JobParameters;Z)V"
        ) { _, args ->
            jobHandler.wrapOnStopJob(
                params = args[0] as JobParameters,
                reschedule = args[1] as Boolean
            )
        }

        environment.artTooling().registerEntryHook(
            JobService::class.java,
            "jobFinished(Landroid/app/job/JobParameters;Z)V"
        ) { _, args ->
            jobHandler.wrapJobFinished(
                params = args[0] as JobParameters,
                wantsReschedule = args[1] as Boolean
            )
        }
    }
}
