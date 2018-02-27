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

    public void scheduleJob() throws Exception {
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
        System.out.println("JOB SCHEDULED");
    }

    public void startJob() {
        new JobServiceEngine(new MyJobService()).startJob(createJobParams(2));
    }

    public void stopJob() {
        new JobServiceEngine(new MyJobService()).stopJob(createJobParams(3));
    }

    public void finishJob() {
        new MyJobService().jobFinished(createJobParams(4), true);
        System.out.println("JOB FINISHED");
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
