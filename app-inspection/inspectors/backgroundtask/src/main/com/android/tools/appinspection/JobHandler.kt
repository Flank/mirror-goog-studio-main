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

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import androidx.inspection.Connection
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.NetworkType
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobScheduled
import com.android.tools.appinspection.BackgroundTaskUtil.sendBackgroundTaskEvent
import com.android.tools.appinspection.common.getStackTrace

/**
 * A handler class that adds necessary hooks to track job related events.
 */
interface JobHandler {

    /**
     * Entry hook for [JobScheduler.schedule].
     *
     * @param job the job parameter passed to the original method.
     */
    fun onScheduleJobEntry(job: JobInfo)

    /**
     * Exit hook for [JobScheduler.schedule]. Capture the result from return value.
     *
     * @param scheduleResult the wrapped return value.
     * @return the same wrapped return value.
     */
    fun onScheduleJobExit(scheduleResult: Int): Int

    /**
     * Wraps JobHandler#ackStartMessage, which is called when
     * [JobService.onStartJob] is called.
     *
     * @param params the params parameter passed to the original method.
     * @param workOngoing the workOngoing parameter passed to the original method.
     */
    fun wrapOnStartJob(params: JobParameters, workOngoing: Boolean)

    /**
     * Wraps [JobHandler.ackStopMessage], which is called when
     * [JobService.onStopJob] is called.
     *
     * @param jobHandler the wrapped JobHandler instance, i.e. "this".
     * @param params the params parameter passed to the original method.
     * @param reschedule the reschedule parameter passed to the original method.
     */
    fun wrapOnStopJob(params: JobParameters, reschedule: Boolean)

    /**
     * Wraps [JobService.jobFinished].
     *
     * @param params the params parameter passed to the original method.
     * @param wantsReschedule the wantsReschedule parameter passed to the original method.
     */
    fun wrapJobFinished(params: JobParameters, wantsReschedule: Boolean)
}

class JobHandlerImpl(private val connection: Connection) : JobHandler {

    /**
     * Use a thread-local variable for schedule job parameters, so a value can be temporarily
     * stored when we enter [JobScheduler.schedule(JobInfo)] and retrieved when we exit it.
     * Using a ThreadLocal protects against the situation when multiple threads schedule jobs
     * at the same time.
     */
    private val scheduleJobInfo = ThreadLocal<JobInfo>()

    /**
     * Job ID is user-defined so we still need to send event ID to guarantee uniqueness across
     * energy events.
     */
    private val jobIdToEventId = mutableMapOf<Int, Long>()

    override fun onScheduleJobEntry(job: JobInfo) {
        scheduleJobInfo.set(job)
    }

    override fun onScheduleJobExit(scheduleResult: Int): Int {
        val jobInfo = scheduleJobInfo.get() ?: return scheduleResult
        scheduleJobInfo.remove()
        val eventId = jobIdToEventId.getOrPut(jobInfo.id) { BackgroundTaskUtil.nextId() }

        connection.sendBackgroundTaskEvent(eventId) {
            stacktrace = getStackTrace(2)
            jobScheduledBuilder.apply {
                jobBuilder.apply {
                    jobId = jobInfo.id
                    serviceName = jobInfo.service.className
                    backoffPolicy = when (jobInfo.backoffPolicy) {
                        JobInfo.BACKOFF_POLICY_EXPONENTIAL -> BackoffPolicy.BACKOFF_POLICY_EXPONENTIAL
                        JobInfo.BACKOFF_POLICY_LINEAR -> BackoffPolicy.BACKOFF_POLICY_LINEAR
                        else -> BackoffPolicy.UNDEFINED_BACKOFF_POLICY
                    }
                    initialBackoffMs = jobInfo.initialBackoffMillis
                    isPeriodic = jobInfo.isPeriodic
                    flexMs = jobInfo.flexMillis
                    intervalMs = jobInfo.intervalMillis
                    minLatencyMs = jobInfo.minLatencyMillis
                    maxExecutionDelayMs = jobInfo.maxExecutionDelayMillis
                    networkType = when (jobInfo.networkType) {
                        JobInfo.NETWORK_TYPE_ANY -> NetworkType.NETWORK_TYPE_ANY
                        JobInfo.NETWORK_TYPE_METERED -> NetworkType.NETWORK_TYPE_METERED
                        JobInfo.NETWORK_TYPE_NONE -> NetworkType.NETWORK_TYPE_NONE
                        JobInfo.NETWORK_TYPE_NOT_ROAMING -> NetworkType.NETWORK_TYPE_NOT_ROAMING
                        JobInfo.NETWORK_TYPE_UNMETERED -> NetworkType.NETWORK_TYPE_UNMETERED
                        else -> NetworkType.UNDEFINED_NETWORK_TYPE
                    }
                    jobInfo.triggerContentUris?.forEach {
                        addTriggerContentUris(it.uri.toString())
                    }
                    triggerContentMaxDelay = jobInfo.triggerContentMaxDelay
                    triggerContentUpdateDelay = jobInfo.triggerContentUpdateDelay
                    isPersisted = jobInfo.isPersisted
                    isRequireBatteryNotLow = jobInfo.isRequireBatteryNotLow
                    isRequireCharging = jobInfo.isRequireCharging
                    isRequireDeviceIdle = jobInfo.isRequireDeviceIdle
                    isRequireStorageNotLow = jobInfo.isRequireStorageNotLow
                    extras = jobInfo.extras.toString()
                    transientExtras = jobInfo.transientExtras.toString()
                }
                result = when (scheduleResult) {
                    JobScheduler.RESULT_SUCCESS -> JobScheduled.Result.RESULT_SUCCESS
                    JobScheduler.RESULT_FAILURE -> JobScheduled.Result.RESULT_FAILURE
                    else -> JobScheduled.Result.UNDEFINED_JOB_SCHEDULE_RESULT
                }
            }
        }
        return scheduleResult
    }

    override fun wrapOnStartJob(params: JobParameters, workOngoing: Boolean) {
        val eventId = jobIdToEventId.getOrPut(params.jobId) { BackgroundTaskUtil.nextId() }
        connection.sendBackgroundTaskEvent(eventId) {
            jobStartedBuilder.apply {
                paramsBuilder.setUp(params)
                this.workOngoing = workOngoing
            }
        }
    }

    override fun wrapOnStopJob(params: JobParameters, reschedule: Boolean) {
        val eventId = jobIdToEventId.getOrPut(params.jobId) { BackgroundTaskUtil.nextId() }
        connection.sendBackgroundTaskEvent(eventId) {
            jobStoppedBuilder.apply {
                paramsBuilder.setUp(params)
                this.reschedule = reschedule
            }.build()
        }
    }

    override fun wrapJobFinished(params: JobParameters, wantsReschedule: Boolean) {
        val eventId = jobIdToEventId.getOrPut(params.jobId) { BackgroundTaskUtil.nextId() }
        connection.sendBackgroundTaskEvent(eventId) {
            stacktrace = getStackTrace(1)
            jobFinishedBuilder.apply {
                paramsBuilder.setUp(params)
                needsReschedule = wantsReschedule
            }
        }
    }

    private fun BackgroundTaskInspectorProtocol.JobParameters.Builder.setUp(params: JobParameters) {
        jobId = params.jobId
        addAllTriggeredContentAuthorities(
            params.triggeredContentAuthorities?.toList() ?: listOf()
        )
        addAllTriggeredContentUris(
            params.triggeredContentUris?.toList()?.map { it.toString() } ?: listOf())
        isOverrideDeadlineExpired = params.isOverrideDeadlineExpired
        extras = params.extras.toString()
        transientExtras = params.transientExtras.toString()
    }
}
