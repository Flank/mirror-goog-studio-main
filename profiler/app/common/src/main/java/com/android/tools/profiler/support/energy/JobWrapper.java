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
import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of helpers for Android job scheduler instrumentation, used by the Energy Profiler.
 *
 * <p>Several classes related to jobs are instrumented: {@link JobScheduler}, {@link JobService},
 * etc.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class JobWrapper {
    /**
     * Use a thread-local variable for schedule job parameters, so a value can be temporarily stored
     * when we enter {@link JobScheduler#schedule(JobInfo)} and retrieved when we exit it. Using a
     * ThreadLocal protects against the situation when multiple threads schedule jobs at the same
     * time.
     */
    private static final ThreadLocal<JobInfo> scheduleJobInfo = new ThreadLocal<JobInfo>();

    /**
     * Job ID is user-defined so we still need to send event ID to guarantee uniqueness across
     * energy events.
     */
    private static final Map<Integer, Integer> jobIdToEventId = new HashMap<Integer, Integer>();

    /**
     * Entry hook for {@link JobScheduler#schedule(JobInfo)}.
     *
     * @param jobScheduler the wrapped JobSchedulerImpl instance, i.e. "this".
     * @param job the job parameter passed to the original method.
     */
    public static void onScheduleJobEntry(Object jobScheduler, JobInfo job) {
        scheduleJobInfo.set(job);
    }

    /**
     * Exit hook for {@link JobScheduler#schedule(JobInfo)}. Capture the result from return value.
     *
     * @param scheduleResult the wrapped return value.
     * @return the same wrapped return value.
     */
    public static int onScheduleJobExit(int scheduleResult) {
        JobInfo jobInfo = scheduleJobInfo.get();
        jobIdToEventId.put(jobInfo.getId(), EventIdGenerator.nextId());
        sendJobScheduled(
                jobIdToEventId.get(jobInfo.getId()),
                jobInfo.getId(),
                jobInfo.getService().getClassName(),
                jobInfo.getBackoffPolicy(),
                jobInfo.getInitialBackoffMillis(),
                jobInfo.isPeriodic(),
                jobInfo.getFlexMillis(),
                jobInfo.getIntervalMillis(),
                jobInfo.getMinLatencyMillis(),
                jobInfo.getMaxExecutionDelayMillis(),
                jobInfo.getNetworkType(),
                triggerContentUrisToStrings(jobInfo.getTriggerContentUris()),
                jobInfo.getTriggerContentMaxDelay(),
                jobInfo.getTriggerContentUpdateDelay(),
                jobInfo.isPersisted(),
                jobInfo.isRequireBatteryNotLow(),
                jobInfo.isRequireCharging(),
                jobInfo.isRequireDeviceIdle(),
                jobInfo.isRequireStorageNotLow(),
                jobInfo.getExtras().toString(),
                jobInfo.getTransientExtras().toString(),
                scheduleResult);
        return scheduleResult;
    }

    /**
     * Wraps JobHandler#ackStartMessage, which is called when {@link
     * JobService#onStartJob(JobParameters)} is called.
     *
     * @param jobHandler the wrapped JobHandler instance, i.e. "this".
     * @param params the params parameter passed to the original method.
     * @param workOngoing the workOngoing parameter passed to the original method.
     */
    public static void wrapOnStartJob(
            Object jobHandler, JobParameters params, boolean workOngoing) {
        sendJobStarted(
                jobIdToEventId.containsKey(params.getJobId())
                        ? jobIdToEventId.get(params.getJobId())
                        : 0,
                params.getJobId(),
                params.getTriggeredContentAuthorities(),
                urisToStrings(params.getTriggeredContentUris()),
                params.isOverrideDeadlineExpired(),
                params.getExtras().toString(),
                params.getTransientExtras().toString(),
                workOngoing);
    }

    /**
     * Wraps JobHandler#ackStopMessage, which is called when {@link
     * JobService#onStopJob(JobParameters)} is called.
     *
     * @param jobHandler the wrapped JobHandler instance, i.e. "this".
     * @param params the params parameter passed to the original method.
     * @param reschedule the reschedule parameter passed to the original method.
     */
    public static void wrapOnStopJob(Object jobHandler, JobParameters params, boolean reschedule) {
        sendJobStopped(
                jobIdToEventId.containsKey(params.getJobId())
                        ? jobIdToEventId.get(params.getJobId())
                        : 0,
                params.getJobId(),
                params.getTriggeredContentAuthorities(),
                urisToStrings(params.getTriggeredContentUris()),
                params.isOverrideDeadlineExpired(),
                params.getExtras().toString(),
                params.getTransientExtras().toString(),
                reschedule);
    }

    /**
     * Wraps {@link JobService#jobFinished(JobParameters, boolean)}.
     *
     * @param jobService the wrapped {@link JobService} instance, i.e. "this".
     * @param params the params parameter passed to the original method.
     * @param wantsReschedule the wantsReschedule parameter passed to the original method.
     */
    public static void wrapJobFinished(
            JobService jobService, JobParameters params, boolean wantsReschedule) {
        sendJobFinished(
                jobIdToEventId.containsKey(params.getJobId())
                        ? jobIdToEventId.get(params.getJobId())
                        : 0,
                params.getJobId(),
                params.getTriggeredContentAuthorities(),
                urisToStrings(params.getTriggeredContentUris()),
                params.isOverrideDeadlineExpired(),
                params.getExtras().toString(),
                params.getTransientExtras().toString(),
                wantsReschedule);
    }

    private static String[] triggerContentUrisToStrings(JobInfo.TriggerContentUri[] uris) {
        if (uris == null) {
            return new String[0];
        }
        String[] result = new String[uris.length];
        for (int i = 0; i < uris.length; ++i) {
            result[i] = uris[i].getUri().toString();
        }
        return result;
    }

    private static String[] urisToStrings(Uri[] uris) {
        if (uris == null) {
            return new String[0];
        }
        String[] result = new String[uris.length];
        for (int i = 0; i < uris.length; ++i) {
            result[i] = uris[i].toString();
        }
        return result;
    }

    // Native functions to send job events to perfd.
    private static native void sendJobScheduled(
            int eventId,
            int jobId,
            String serviceName,
            int backoffPolicy,
            long initialBackoffMs,
            boolean isPeriodic,
            long flexMs,
            long intervalMs,
            long minLatencyMs,
            long maxExecutionDelayMs,
            int networkType,
            String[] triggerContentUris,
            long triggerContentMaxDelay,
            long triggerContentUpdateDelay,
            boolean isPersisted,
            boolean isRequireBatteryNotLow,
            boolean isRequireCharging,
            boolean isRequireDeviceIdle,
            boolean isRequireStorageNotLow,
            String extras,
            String transientExtras,
            int scheduleResult);

    private static native void sendJobStarted(
            int eventId,
            int jobId,
            String[] triggerContentAuthorities,
            String[] triggerContentUris,
            boolean isOverrideDeadlineExpired,
            String extras,
            String transientExtras,
            boolean workOngoing);

    private static native void sendJobStopped(
            int eventId,
            int jobId,
            String[] triggerContentAuthorities,
            String[] triggerContentUris,
            boolean isOverrideDeadlineExpired,
            String extras,
            String transientExtras,
            boolean reschedule);

    private static native void sendJobFinished(
            int eventId,
            int jobId,
            String[] triggerContentAuthorities,
            String[] triggerContentUris,
            boolean isOverrideDeadlineExpired,
            String extras,
            String transientExtras,
            boolean needsReschedule);
}
