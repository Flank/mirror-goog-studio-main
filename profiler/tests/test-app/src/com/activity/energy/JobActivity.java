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

package com.activity.energy;

import android.app.job.JobInfo.Builder;
import android.app.job.JobInfo.TriggerContentUri;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.job.JobServiceEngine;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import com.activity.PerfdTestActivity;

public class JobActivity extends PerfdTestActivity {
    public JobActivity() {
        super("Job Activity");
    }

    public void scheduleStartAndFinishJob() throws Exception {
        Builder jobBuilder =
                new Builder(1, new ComponentName("com.example"))
                        .setBackoffCriteria(100, 1)
                        .setPeriodic(200, 300)
                        .setMinimumLatency(400)
                        .setOverrideDeadline(500)
                        .setRequiresNetworkType(4)
                        .addTriggerContentUri(new TriggerContentUri(new Uri("foo.bar")))
                        .setTriggerContentMaxDelay(600)
                        .setTriggerContentUpdateDelay(700)
                        .setPersisted(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiresStorageNotLow(true)
                        .setExtras(new PersistableBundle("extras"))
                        .setTransientExtras(new Bundle("transient extras"));
        getJobSchedler().schedule(jobBuilder.build());
        JobService jobService = new MyJobService();
        JobServiceEngine engine = new JobServiceEngine(jobService);
        JobParameters jobParams = createJobParams(1);
        engine.startJob(jobParams);
        jobService.jobFinished(jobParams, true);
        System.out.println("JOB FINISHED");
    }

    public void scheduleStartAndStopJob() {
        getJobSchedler().schedule(new Builder(2, new ComponentName("com.example")).build());
        JobServiceEngine engine = new JobServiceEngine(new MyJobService());
        JobParameters jobParams = createJobParams(2);
        engine.startJob(jobParams);
        engine.stopJob(jobParams);
    }

    private JobParameters createJobParams(int jobId) {
        return new JobParameters(
                jobId,
                new PersistableBundle("extras"),
                new Bundle("transient extras"),
                true,
                new Uri[] {new Uri("com.example")},
                new String[] {"foo@example.com"});
    }
}
