/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.profiler.app.inspection;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand;
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand;
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AppInspectionTest {

    private static final String ACTIVITY_CLASS = "com.activity.MyActivity";

    @Rule public final PerfDriver perfDriver = new PerfDriver(ACTIVITY_CLASS, 26, true);
    private ServiceLayer serviceLayer;

    @Before
    public void setUp() throws Exception {
        serviceLayer = ServiceLayer.create(perfDriver);
    }

    @Test
    public void testStub() throws Exception {
        AppInspectionCommand enableCommand =
                AppInspectionCommand.newBuilder()
                        .setCreateInspectorCommand(CreateInspectorCommand.getDefaultInstance())
                        .build();
        AppInspectionEvent enableEvent = serviceLayer.sendCommand(enableCommand);
        assertThat(enableEvent.hasResponse()).isTrue();
        assertThat(enableEvent.getResponse().getStatus()).isEqualTo(Status.ERROR);

        AppInspectionCommand disableCommand =
                AppInspectionCommand.newBuilder()
                        .setDisposeInspectorCommand(DisposeInspectorCommand.getDefaultInstance())
                        .build();
        AppInspectionEvent disableResponse = serviceLayer.sendCommand(disableCommand);
        assertThat(disableResponse.hasResponse()).isTrue();
        assertThat(disableResponse.getResponse().getStatus()).isEqualTo(Status.ERROR);
    }

    static class ServiceLayer {
        private static final long TIMEOUT_SECONDS = 30;

        private final ExecutorService executor;
        private final TransportServiceBlockingStub transportStub;
        private final Iterator<Common.Event> eventsIterator;
        private final int pid;

        private ServiceLayer(
                ExecutorService executor,
                TransportServiceBlockingStub transportStub,
                Iterator<Common.Event> eventsIterator,
                int pid) {
            this.executor = executor;
            this.transportStub = transportStub;
            this.eventsIterator = eventsIterator;
            this.pid = pid;
        }

        static ServiceLayer create(PerfDriver driver) throws Exception {
            TransportServiceBlockingStub transportStub = driver.getGrpc().getTransportStub();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Iterator<Common.Event>> future =
                    executor.submit(
                            () -> transportStub.getEvents(GetEventsRequest.getDefaultInstance()));
            Iterator<Common.Event> eventsIterator = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new ServiceLayer(
                    executor, transportStub, eventsIterator, driver.getSession().getPid());
        }

        AppInspectionEvent sendCommand(AppInspectionCommand appInspectionCommand) throws Exception {
            Commands.Command command =
                    Commands.Command.newBuilder()
                            .setType(Commands.Command.CommandType.APP_INSPECTION)
                            .setAndroidxInspectionCommand(appInspectionCommand)
                            .setStreamId(1234)
                            .setPid(pid)
                            .build();

            ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setCommand(command).build();
            AppInspectionEvent appInspectionEvent =
                    executor.submit(
                                    () -> {
                                        transportStub.execute(executeRequest);
                                        while (eventsIterator.hasNext()) {
                                            Common.Event event = eventsIterator.next();
                                            if (event.hasAppInspectionEvent()) {
                                                return event.getAppInspectionEvent();
                                            }
                                        }
                                        return null;
                                    })
                            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(appInspectionEvent).isNotNull();
            return appInspectionEvent;
        }
    }
}
