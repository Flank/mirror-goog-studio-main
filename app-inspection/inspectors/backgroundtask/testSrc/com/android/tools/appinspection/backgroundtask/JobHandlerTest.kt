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

package com.android.tools.appinspection.backgroundtask

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.BackoffPolicy
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.JobInfo.NetworkType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class JobHandlerTest {

    @get:Rule
    val inspectorRule = BackgroundTaskInspectorRule()

    private val fakeParameters = JobParameters(
        1,
        PersistableBundle("Extra"),
        Bundle("TransientExtras"),
        true,
        arrayOf(),
        arrayOf("authority")
    )

    @Test
    fun jobScheduled() {
        val jobHandler = inspectorRule.inspector.jobHandler
        val job = JobInfo.Builder(1, ComponentName("TestClass"))
            .setBackoffCriteria(100, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPeriodic(101, 102)
            .setMinimumLatency(103)
            .setOverrideDeadline(104)
            .setRequiresNetworkType(JobInfo.NETWORK_TYPE_METERED)
            .setTriggerContentMaxDelay(105)
            .setTriggerContentUpdateDelay(106)
            .setPersisted(true)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .setRequiresStorageNotLow(true)
            .setExtras(PersistableBundle("Extra"))
            .setTransientExtras(Bundle("TransientExtras"))
            .addTriggerContentUri(JobInfo.TriggerContentUri(Uri()))
            .build()
        jobHandler.onScheduleJobEntry(job)
        jobHandler.onScheduleJobExit(0)
        inspectorRule.connection.consume {
            with(jobScheduled.job) {
                assertThat(jobId).isEqualTo(1)
                assertThat(serviceName).isEqualTo("TestClass")
                assertThat(backoffPolicy).isEqualTo(BackoffPolicy.BACKOFF_POLICY_EXPONENTIAL)
                assertThat(initialBackoffMs).isEqualTo(100)
                assertThat(isPeriodic).isEqualTo(true)
                assertThat(flexMs).isEqualTo(102)
                assertThat(intervalMs).isEqualTo(101)
                assertThat(minLatencyMs).isEqualTo(103)
                assertThat(maxExecutionDelayMs).isEqualTo(104)
                assertThat(networkType).isEqualTo(NetworkType.NETWORK_TYPE_METERED)
                assertThat(triggerContentMaxDelay).isEqualTo(105)
                assertThat(triggerContentUpdateDelay).isEqualTo(106)
                assertThat(isPersisted).isEqualTo(true)
                assertThat(isRequireCharging).isEqualTo(true)
                assertThat(isRequireDeviceIdle).isEqualTo(true)
                assertThat(isRequireStorageNotLow).isEqualTo(true)
                assertThat(extras).isEqualTo("Extra")
                assertThat(transientExtras).isEqualTo("TransientExtras")
                assertThat(triggerContentUrisList.size).isEqualTo(1)
                assertThat(triggerContentUrisList[0]).isEqualTo("uri")
            }
        }
    }

    @Test
    fun jobStarted() {
        val jobHandler = inspectorRule.inspector.jobHandler
        jobHandler.wrapOnStartJob(fakeParameters, true)
        inspectorRule.connection.consume {
            with(jobStarted) {
                with(params) {
                    assertThat(jobId).isEqualTo(1)
                    assertThat(extras).isEqualTo("Extra")
                    assertThat(transientExtras).isEqualTo("TransientExtras")
                    assertThat(isOverrideDeadlineExpired).isTrue()
                    assertThat(triggeredContentUrisCount).isEqualTo(0)
                    assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
                    assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
                }
                assertThat(workOngoing).isTrue()
            }
        }
    }

    @Test
    fun jobStopped() {
        val jobHandler = inspectorRule.inspector.jobHandler
        jobHandler.wrapOnStopJob(fakeParameters, true)
        inspectorRule.connection.consume {
            with(jobStopped) {
                with(params) {
                    assertThat(jobId).isEqualTo(1)
                    assertThat(extras).isEqualTo("Extra")
                    assertThat(transientExtras).isEqualTo("TransientExtras")
                    assertThat(isOverrideDeadlineExpired).isTrue()
                    assertThat(triggeredContentUrisCount).isEqualTo(0)
                    assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
                    assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
                }
                assertThat(reschedule).isTrue()
            }
        }
    }

    @Test
    fun jobFinished() {
        val jobHandler = inspectorRule.inspector.jobHandler
        jobHandler.wrapJobFinished(fakeParameters, true)
        inspectorRule.connection.consume {
            with(jobFinished) {
                with(params) {
                    assertThat(jobId).isEqualTo(1)
                    assertThat(extras).isEqualTo("Extra")
                    assertThat(transientExtras).isEqualTo("TransientExtras")
                    assertThat(isOverrideDeadlineExpired).isTrue()
                    assertThat(triggeredContentUrisCount).isEqualTo(0)
                    assertThat(triggeredContentAuthoritiesCount).isEqualTo(1)
                    assertThat(triggeredContentAuthoritiesList[0]).isEqualTo("authority")
                }
                assertThat(needsReschedule).isTrue()
            }
        }
    }
}
