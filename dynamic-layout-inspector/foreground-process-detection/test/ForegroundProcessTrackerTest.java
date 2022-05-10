/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.layoutinspector.foregroundprocesstracker;

import static com.google.common.truth.Truth.*;

import com.android.annotations.NonNull;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.transport.TransportRule;
import com.android.tools.transport.device.SdkLevel;
import com.android.tools.transport.grpc.TransportAsyncStubWrapper;
import com.google.common.collect.Lists;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForegroundProcessTrackerTest {
    @NonNull
    @Parameters
    public static Collection<SdkLevel> parameters() {
        // Test both pre- and post-JVMTI devices.
        return Lists.newArrayList(SdkLevel.M, SdkLevel.O);
    }

    public static final String ACTIVITY_CLASS = "com.activity.SimpleActivity";
    @Rule public final TransportRule myTransportRule;

    public ForegroundProcessTrackerTest(@NonNull SdkLevel sdkLevel) {
        myTransportRule = new TransportRule(ACTIVITY_CLASS, sdkLevel);
    }

    @Test
    public void verifyBehaviorByUsingGrpcApis() {
        TransportAsyncStubWrapper transportWrapper =
                TransportAsyncStubWrapper.create(myTransportRule.getGrpc());
        TransportServiceGrpc.TransportServiceBlockingStub transportStub =
                TransportServiceGrpc.newBlockingStub(myTransportRule.getGrpc().getChannel());

        sendStartPollingCommand(transportStub);

        final int[] count = {0};

        transportWrapper.getEvents(
                event -> {
                    if (event.getKind() == Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS) {
                        String pid = event.getLayoutInspectorForegroundProcess().getPid();
                        String processName =
                                event.getLayoutInspectorForegroundProcess().getProcessName();

                        if (count[0] % 2 == 0) {
                            assertThat(pid).isEqualTo("1");
                            assertThat(processName).isEqualTo("fake.process1");
                        } else {
                            assertThat(pid).isEqualTo("2");
                            assertThat(processName).isEqualTo("fake.process2");
                        }

                        count[0] += 1;
                    }

                    if (count[0] == 2) {
                        sendStopPollingCommand(transportStub);
                    }
                    return count[0] == 2;
                },
                event -> (event.getKind() == Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS),
                () -> {});

        assertThat(count[0]).isEqualTo(2);
    }

    private void sendStartPollingCommand(
            TransportServiceGrpc.TransportServiceBlockingStub transportStub) {
        sendCommand(transportStub, Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS);
    }

    private void sendStopPollingCommand(
            TransportServiceGrpc.TransportServiceBlockingStub transportStub) {
        sendCommand(transportStub, Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS);
    }

    private void sendCommand(
            TransportServiceGrpc.TransportServiceBlockingStub transportStub,
            Commands.Command.CommandType commandType) {
        Commands.Command command =
                Commands.Command.newBuilder().setType(commandType).setStreamId(1234).build();

        ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setCommand(command).build();
        transportStub.execute(executeRequest);
    }
}
