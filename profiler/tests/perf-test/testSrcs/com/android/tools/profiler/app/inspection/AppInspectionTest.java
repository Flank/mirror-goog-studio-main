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

import static com.android.tools.app.inspection.AppInspection.ServiceResponse.Status.SUCCESS;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand;
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand;
import com.android.tools.app.inspection.AppInspection.RawCommand;
import com.android.tools.app.inspection.AppInspection.ServiceResponse.Status;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private static final String EXPECTED_INSPECTOR_PREFIX = "TEST INSPECTOR ";
    private static final String EXPECTED_INSPECTOR_CREATED = EXPECTED_INSPECTOR_PREFIX + "CREATED";
    private static final String EXPECTED_INSPECTOR_DISPOSED =
            EXPECTED_INSPECTOR_PREFIX + "DISPOSED";

    @Rule public final PerfDriver perfDriver = new PerfDriver(ACTIVITY_CLASS, 26, true);
    private ServiceLayer serviceLayer;
    private FakeAndroidDriver myAndroidDriver;

    @Before
    public void setUp() throws Exception {
        myAndroidDriver = perfDriver.getFakeAndroidDriver();
        serviceLayer = ServiceLayer.create(perfDriver);
    }

    @Test
    public void createThenDispose() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(serviceLayer.sendCommand(disposeInspector("test.inspector")), SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void doubleInspectorCreation() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)),
                Status.ERROR);
        assertResponseStatus(serviceLayer.sendCommand(disposeInspector("test.inspector")), SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void disposeNonexistent() throws Exception {
        assertResponseStatus(
                serviceLayer.sendCommand(disposeInspector("test.inspector")), Status.ERROR);
    }

    @Test
    public void createFailsWithUnknownInspectorId() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("foo", onDevicePath)), Status.ERROR);
    }

    @Test
    public void createFailsIfInspectorDexIsNonexistent() throws Exception {
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", "random_file")),
                Status.ERROR);
    }

    @Test
    public void sendBasicCommand() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                serviceLayer.sendCommand(
                        rawCommandInspector("test.inspector", new byte[] {1, 2, 127})),
                SUCCESS);
        myAndroidDriver.waitForInput(EXPECTED_INSPECTOR_PREFIX + "[1, 2, 127]");
    }

    private static AppInspectionCommand rawCommandInspector(
            String inspectorId, byte[] commandData) {
        return AppInspectionCommand.newBuilder()
                .setRawInspectorCommand(
                        RawCommand.newBuilder()
                                .setInspectorId(inspectorId)
                                .setRawCommand(ByteString.copyFrom(commandData))
                                .build())
                .build();
    }

    private static AppInspectionCommand createInspector(String inspectorId, String dexPath) {
        return AppInspectionCommand.newBuilder()
                .setCreateInspectorCommand(
                        CreateInspectorCommand.newBuilder()
                                .setInspectorId(inspectorId)
                                .setDexPath(dexPath)
                                .build())
                .build();
    }

    private static AppInspectionCommand disposeInspector(String inspectorId) {
        return AppInspectionCommand.newBuilder()
                .setDisposeInspectorCommand(
                        DisposeInspectorCommand.newBuilder().setInspectorId(inspectorId).build())
                .build();
    }

    private static void assertResponseStatus(AppInspectionEvent event, Status expected) {
        assertThat(event.hasResponse()).isTrue();
        assertThat(event.getResponse().getStatus()).isEqualTo(expected);
    }

    private static String injectInspectorDex() throws IOException {
        File inspectorDex = new File(ProcessRunner.getProcessPath("test.inspector.dex.location"));
        assertThat(inspectorDex.exists()).isTrue();
        String onDevicePath = "test_inspector.jar";
        Files.copy(inspectorDex.toPath(), new File(onDevicePath).toPath(), REPLACE_EXISTING);
        return onDevicePath;
    }

    static class ServiceLayer {
        private static final long TIMEOUT_SECONDS = 30;

        private final ExecutorService executor;
        private final TransportServiceBlockingStub transportStub;
        private final Iterator<Common.Event> eventsIterator;
        private final int pid;
        private int nextCommandId = 1;

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

        int commandId = nextCommandId++;
        AppInspectionEvent sendCommand(AppInspectionCommand appInspectionCommand) throws Exception {
            Commands.Command command =
                    Commands.Command.newBuilder()
                            .setType(Commands.Command.CommandType.APP_INSPECTION)
                            .setAndroidxInspectionCommand(appInspectionCommand)
                            .setStreamId(1234)
                            .setPid(pid)
                            .setCommandId(commandId)
                            .build();

            ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setCommand(command).build();
            AppInspectionEvent appInspectionEvent =
                    executor.submit(
                                    () -> {
                                        transportStub.execute(executeRequest);
                                        while (eventsIterator.hasNext()) {
                                            Common.Event event = eventsIterator.next();
                                            if (event.hasAppInspectionEvent()
                                                    && event.getCommandId() == commandId) {
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
