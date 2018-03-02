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

package com.android.tools.profiler.energy;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent.MetadataCase;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.profiler.proto.EnergyProfiler.JobFinished;
import com.android.tools.profiler.proto.EnergyProfiler.JobInfo;
import com.android.tools.profiler.proto.EnergyProfiler.JobInfo.BackoffPolicy;
import com.android.tools.profiler.proto.EnergyProfiler.JobInfo.NetworkType;
import com.android.tools.profiler.proto.EnergyProfiler.JobParameters;
import com.android.tools.profiler.proto.EnergyProfiler.JobScheduled;
import com.android.tools.profiler.proto.EnergyProfiler.JobScheduled.Result;
import com.android.tools.profiler.proto.EnergyProfiler.JobStarted;
import com.android.tools.profiler.proto.EnergyProfiler.JobStopped;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JobTest {
    private static final String ACTIVITY_CLASS = "com.activity.energy.JobActivity";

    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myStubWrapper;
    private Session mySession;

    @Before
    public void setUp() throws Exception {
        myPerfDriver = new PerfDriver(true);
        myPerfDriver.start(ACTIVITY_CLASS);
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
        myStubWrapper = new EnergyStubWrapper(myGrpc.getEnergyStub());
        mySession =
                myGrpc.beginSessionWithAgent(
                        myPerfDriver.getPid(), myPerfDriver.getCommunicationPort());
    }

    @After
    public void tearDown() throws Exception {
        myGrpc.endSession(mySession.getSessionId());
        myPerfDriver.tearDown();
    }

    @Test
    public void testScheduleJob() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "scheduleJob");
        assertThat(myAndroidDriver.waitForInput("JOB SCHEDULED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 1);
        assertThat(response.getEventsCount()).isEqualTo(1);

        final EnergyEvent energyEvent = response.getEvents(0);
        assertThat(energyEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(energyEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(energyEvent.getEventId()).isGreaterThan(0);
        assertThat(energyEvent.getIsTerminal()).isFalse();
        assertThat(energyEvent.getMetadataCase()).isEqualTo(MetadataCase.JOB_SCHEDULED);
        JobScheduled jobScheduled = energyEvent.getJobScheduled();
        assertThat(jobScheduled.getResult()).isEqualTo(Result.RESULT_SUCCESS);
        JobInfo jobInfo = jobScheduled.getJob();
        assertThat(jobInfo.getJobId()).isEqualTo(1);
        assertThat(jobInfo.getServiceName()).isEqualTo("com.example");
        assertThat(jobInfo.getBackoffPolicy()).isEqualTo(BackoffPolicy.BACKOFF_POLICY_EXPONENTIAL);
        assertThat(jobInfo.getInitialBackoffMs()).isEqualTo(100);
        assertThat(jobInfo.getIsPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMs()).isEqualTo(200);
        assertThat(jobInfo.getFlexMs()).isEqualTo(300);
        assertThat(jobInfo.getMinLatencyMs()).isEqualTo(400);
        assertThat(jobInfo.getMaxExecutionDelayMs()).isEqualTo(500);
        assertThat(jobInfo.getNetworkType()).isEqualTo(NetworkType.NETWORK_TYPE_METERED);
        assertThat(jobInfo.getTriggerContentUrisList()).containsExactly("foo.bar");
        assertThat(jobInfo.getTriggerContentMaxDelay()).isEqualTo(600);
        assertThat(jobInfo.getTriggerContentUpdateDelay()).isEqualTo(700);
        assertThat(jobInfo.getIsPersisted()).isTrue();
        assertThat(jobInfo.getIsRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.getIsRequireCharging()).isTrue();
        assertThat(jobInfo.getIsRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.getIsRequireStorageNotLow()).isTrue();
        assertThat(jobInfo.getExtras()).isEqualTo("extras");
        assertThat(jobInfo.getTransientExtras()).isEqualTo("transient extras");
    }

    @Test
    public void testStartJob() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "startJob");
        assertThat(myAndroidDriver.waitForInput("JOB STARTED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        final EnergyEvent scheduleEvent = response.getEvents(0);
        final EnergyEvent startEvent = response.getEvents(1);
        assertThat(startEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(startEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(startEvent.getEventId()).isEqualTo(scheduleEvent.getEventId());
        assertThat(startEvent.getIsTerminal()).isFalse();
        assertThat(startEvent.getMetadataCase()).isEqualTo(MetadataCase.JOB_STARTED);
        JobStarted jobStarted = startEvent.getJobStarted();
        assertThat(jobStarted.getWorkOngoing()).isTrue();
        JobParameters params = jobStarted.getParams();
        assertThat(params.getJobId()).isEqualTo(2);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");
    }

    @Test
    public void testStopJob() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "stopJob");
        assertThat(myAndroidDriver.waitForInput("JOB STOPPED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        final EnergyEvent scheduleEvent = response.getEvents(0);
        final EnergyEvent stopEvent = response.getEvents(1);
        assertThat(stopEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(stopEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(stopEvent.getEventId()).isEqualTo(scheduleEvent.getEventId());
        assertThat(stopEvent.getIsTerminal()).isFalse();
        assertThat(stopEvent.getMetadataCase()).isEqualTo(MetadataCase.JOB_STOPPED);
        JobStopped jobStopped = stopEvent.getJobStopped();
        assertThat(jobStopped.getReschedule()).isTrue();
        JobParameters params = jobStopped.getParams();
        assertThat(params.getJobId()).isEqualTo(3);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");
    }

    @Test
    public void testFinishJob() throws Exception {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "finishJob");
        assertThat(myAndroidDriver.waitForInput("JOB FINISHED")).isTrue();

        EnergyEventsResponse response =
                TestUtils.waitForAndReturn(
                        () -> myStubWrapper.getAllEnergyEvents(mySession),
                        resp -> resp.getEventsCount() == 2);
        assertThat(response.getEventsCount()).isEqualTo(2);

        final EnergyEvent scheduleEvent = response.getEvents(0);
        final EnergyEvent finishEvent = response.getEvents(1);
        assertThat(finishEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(finishEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(finishEvent.getEventId()).isEqualTo(scheduleEvent.getEventId());
        assertThat(finishEvent.getIsTerminal()).isTrue();
        assertThat(finishEvent.getMetadataCase()).isEqualTo(MetadataCase.JOB_FINISHED);
        JobFinished jobFinished = finishEvent.getJobFinished();
        assertThat(jobFinished.getNeedsReschedule()).isTrue();
        JobParameters params = jobFinished.getParams();
        assertThat(params.getJobId()).isEqualTo(4);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");
    }
}
