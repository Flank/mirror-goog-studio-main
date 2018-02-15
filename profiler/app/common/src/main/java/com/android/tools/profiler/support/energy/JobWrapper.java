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

package com.android.tools.profiler.support.energy;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import com.android.tools.profiler.support.util.StudioLog;

/**
 * A set of helpers for Android job scheduler instrumentation, used by the Energy Profiler.
 *
 * <p>Several classes related to jobs are instrumented: {@link JobScheduler}, {@link JobService},
 * etc.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class JobWrapper {
    /**
     * Entry hook for {@link JobScheduler#schedule(JobInfo)}.
     *
     * @param jobScheduler the wrapped JobSchedulerImpl instance, i.e. "this".
     * @param job the job parameter passed to the original method.
     */
    public static void onScheduleJobEntry(Object jobScheduler, JobInfo job) {
        StudioLog.v("Scheduling job: " + String.valueOf(job));
    }

    /**
     * Exit hook for {@link JobScheduler#schedule(JobInfo)}. Capture the result from return value.
     *
     * @param wrapped the wrapped return value.
     * @return the same wrapped return value.
     */
    public static int onScheduleJobExit(int wrapped) {
        StudioLog.v(
                "Job scheduled. Result: "
                        + (wrapped == JobScheduler.RESULT_SUCCESS ? "success" : "failure"));
        return wrapped;
    }

    /**
     * Wraps JobHandler#ackStartMessage, which is called when {@link
     * JobService#onStartJob(JobParameters)} is called.
     *
     * @param jobHandler the wrapped JobHandler instance.
     * @param params the params parameter passed to the original method.
     * @param workOngoing the workOngoing parameter passed to the original method.
     */
    public static void wrapOnStartJob(
            Object jobHandler, JobParameters params, boolean workOngoing) {
        StudioLog.v(
                String.format(
                        "onStartJob (JobId=%d, workOngoing=%b)", params.getJobId(), workOngoing));
    }

    /**
     * Wraps JobHandler#ackStopMessage, which is called when {@link
     * JobService#onStopJob(JobParameters)} is called.
     *
     * @param jobHandler the wrapped JobHandler instance.
     * @param params the params parameter passed to the original method.
     * @param reschedule the reschedule parameter passed to the original method.
     */
    public static void wrapOnStopJob(Object jobHandler, JobParameters params, boolean reschedule) {
        StudioLog.v(
                String.format(
                        "onStopJob (JobId=%d, reschedule=%b)", params.getJobId(), reschedule));
    }

    /**
     * Wraps {@link JobService#jobFinished(JobParameters, boolean)}.
     *
     * @param wrapped the wrapped {@link JobService} instance.
     * @param params the params parameter passed to the original method.
     * @param wantsReschedule the wantsReschedule parameter passed to the original method.
     */
    public static void wrapJobFinished(
            JobService wrapped, JobParameters params, boolean wantsReschedule) {
        StudioLog.v(
                String.format(
                        "jobFinished (JobId=%d, wantsReschedule=%b)",
                        params.getJobId(), wantsReschedule));
    }
}
