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
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.TransportStubWrapper;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.Energy.*;
import com.android.tools.profiler.proto.Energy.EnergyEventData.MetadataCase;
import com.android.tools.profiler.proto.Energy.JobInfo.BackoffPolicy;
import com.android.tools.profiler.proto.Energy.JobInfo.NetworkType;
import com.android.tools.profiler.proto.Energy.JobScheduled.Result;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobTest {
    private static final String ACTIVITY_CLASS = "com.activity.energy.JobActivity";
    @Rule public final PerfDriver myPerfDriver;

    private boolean myIsUnifiedPipeline;
    private GrpcUtils myGrpc;
    private FakeAndroidDriver myAndroidDriver;
    private EnergyStubWrapper myEnergyWrapper;
    private TransportStubWrapper myTransportWrapper;
    private Session mySession;

    public JobTest(int sdkLevel, boolean isUnifiedPipeline) {
        myPerfDriver = new PerfDriver(ACTIVITY_CLASS, sdkLevel, isUnifiedPipeline);
        myIsUnifiedPipeline = isUnifiedPipeline;
    }

    @Parameters(name = "{index}: SdkLevel={0}, UnifiedPipeline={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{26, false}, {26, true}, {28, false}, {28, true}});
    }

    @Before
    public void setUp() throws Exception {
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myGrpc = myPerfDriver.getGrpc();
        myEnergyWrapper = new EnergyStubWrapper(myGrpc.getEnergyStub());
        myTransportWrapper = new TransportStubWrapper(myGrpc.getTransportStub());
        mySession = myPerfDriver.getSession();
    }

    @Test
    public void testScheduleStartAndFinishJob() throws Exception {
        final String methodName = "scheduleStartAndFinishJob";
        final String expectedResponse = "JOB FINISHED";

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    myTransportWrapper.getEvents(
                            3,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            (unused) -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> myEnergyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 3);
            energyEvents.addAll(response.getEventsList());
        }
        assertWithMessage(
                        "Actual events: (%s)",
                        energyEvents
                                .stream()
                                .map(
                                        event ->
                                                String.valueOf(
                                                        event.getEnergyEvent().getMetadataCase()))
                                .collect(Collectors.joining(", ")))
                .that(energyEvents)
                .hasSize(3);

        final Common.Event scheduleEvent = energyEvents.get(0);
        assertThat(scheduleEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(scheduleEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(scheduleEvent.getGroupId()).isGreaterThan(0L);
        assertThat(scheduleEvent.getIsEnded()).isFalse();
        assertThat(scheduleEvent.getEnergyEvent().getCallstack()).contains("schedule");
        assertThat(scheduleEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_SCHEDULED);
        JobScheduled jobScheduled = scheduleEvent.getEnergyEvent().getJobScheduled();
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

        final Common.Event startEvent = energyEvents.get(1);
        assertThat(startEvent.getTimestamp()).isAtLeast(scheduleEvent.getTimestamp());
        assertThat(startEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(startEvent.getGroupId()).isEqualTo(scheduleEvent.getGroupId());
        assertThat(startEvent.getIsEnded()).isFalse();
        assertThat(startEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_STARTED);
        JobStarted jobStarted = startEvent.getEnergyEvent().getJobStarted();
        assertThat(jobStarted.getWorkOngoing()).isTrue();
        JobParameters params = jobStarted.getParams();
        assertThat(params.getJobId()).isEqualTo(1);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");

        final Common.Event finishEvent = energyEvents.get(2);
        assertThat(finishEvent.getTimestamp()).isAtLeast(startEvent.getTimestamp());
        assertThat(finishEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(finishEvent.getGroupId()).isEqualTo(scheduleEvent.getGroupId());
        assertThat(finishEvent.getIsEnded()).isTrue();
        assertThat(finishEvent.getEnergyEvent().getCallstack()).contains("Finish");
        assertThat(finishEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_FINISHED);
        JobFinished jobFinished = finishEvent.getEnergyEvent().getJobFinished();
        assertThat(jobFinished.getNeedsReschedule()).isFalse();
        params = jobFinished.getParams();
        assertThat(params.getJobId()).isEqualTo(1);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");
    }

    @Test
    public void testScheduleStartAndStopJob() throws Exception {
        final String methodName = "scheduleStartAndStopJob";
        final String expectedResponse = "JOB STOPPED";

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    myTransportWrapper.getEvents(
                            3,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            (unused) -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> myEnergyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 3);
            energyEvents.addAll(response.getEventsList());
        }
        assertWithMessage(
                        "Actual events: (%s).",
                        energyEvents
                                .stream()
                                .map(
                                        event ->
                                                String.valueOf(
                                                        event.getEnergyEvent().getMetadataCase()))
                                .collect(Collectors.joining(", ")))
                .that(energyEvents)
                .hasSize(3);

        final Common.Event scheduleEvent = energyEvents.get(0);
        final Common.Event startEvent = energyEvents.get(1);
        assertThat(startEvent.getTimestamp()).isGreaterThan(0L);
        assertThat(startEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(startEvent.getGroupId()).isEqualTo(scheduleEvent.getGroupId());
        assertThat(startEvent.getIsEnded()).isFalse();
        assertThat(startEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_STARTED);
        JobStarted jobStarted = startEvent.getEnergyEvent().getJobStarted();
        assertThat(jobStarted.getWorkOngoing()).isTrue();
        JobParameters params = jobStarted.getParams();
        assertThat(params.getJobId()).isEqualTo(2);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");

        final Common.Event stopEvent = energyEvents.get(2);
        assertThat(stopEvent.getTimestamp()).isAtLeast(startEvent.getTimestamp());
        assertThat(stopEvent.getPid()).isEqualTo(mySession.getPid());
        assertThat(stopEvent.getGroupId()).isEqualTo(scheduleEvent.getGroupId());
        assertThat(stopEvent.getIsEnded()).isTrue();
        assertThat(stopEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_STOPPED);
        JobStopped jobStopped = stopEvent.getEnergyEvent().getJobStopped();
        assertThat(jobStopped.getReschedule()).isFalse();
        params = jobStopped.getParams();
        assertThat(params.getJobId()).isEqualTo(2);
        assertThat(params.getExtras()).isEqualTo("extras");
        assertThat(params.getTransientExtras()).isEqualTo("transient extras");
        assertThat(params.getIsOverrideDeadlineExpired()).isTrue();
        assertThat(params.getTriggeredContentAuthoritiesList()).containsExactly("foo@example.com");
        assertThat(params.getTriggeredContentUrisList()).containsExactly("com.example");
    }

    @Test
    public void testMissingJobScheduled() throws Exception {
        final String methodName = "startWithoutScheduling";
        final String expectedResponse = "JOB STARTED";

        List<Common.Event> energyEvents = new ArrayList<>();
        if (myIsUnifiedPipeline) {
            Map<Long, List<Common.Event>> eventGroups =
                    myTransportWrapper.getEvents(
                            1,
                            event -> event.getKind() == Common.Event.Kind.ENERGY_EVENT,
                            (unused) -> triggerMethod(methodName, expectedResponse));
            for (List<Common.Event> eventList : eventGroups.values()) {
                energyEvents.addAll(eventList);
            }
        } else {
            triggerMethod(methodName, expectedResponse);
            EnergyEventsResponse response =
                    TestUtils.waitForAndReturn(
                            () -> myEnergyWrapper.getAllEnergyEvents(mySession),
                            resp -> resp.getEventsCount() == 1);
            energyEvents.addAll(response.getEventsList());
        }
        assertWithMessage(
                        "Actual events: (%s).",
                        energyEvents
                                .stream()
                                .map(
                                        event ->
                                                String.valueOf(
                                                        event.getEnergyEvent().getMetadataCase()))
                                .collect(Collectors.joining(", ")))
                .that(energyEvents)
                .hasSize(1);

        final Common.Event startEvent = energyEvents.get(0);
        assertThat(startEvent.getGroupId()).isGreaterThan(0L);
        assertThat(startEvent.getEnergyEvent().getMetadataCase())
                .isEqualTo(MetadataCase.JOB_STARTED);
    }

    private void triggerMethod(String methodName, String expectedResponse) {
        myAndroidDriver.triggerMethod(ACTIVITY_CLASS, methodName);
        assertThat(myAndroidDriver.waitForInput(expectedResponse)).isTrue();
    }
}
