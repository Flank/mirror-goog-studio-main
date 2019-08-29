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
import static com.android.tools.profiler.app.inspection.ServiceLayer.TIMEOUT_SECONDS;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import org.junit.After;
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
    private FakeAndroidDriver androidDriver;

    @Before
    public void setUp() throws Exception {
        androidDriver = perfDriver.getFakeAndroidDriver();
        serviceLayer = ServiceLayer.create(perfDriver);
    }

    @After
    public void tearDown() {
        serviceLayer.shutdown();
    }

    @Test
    public void createThenDispose() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(serviceLayer.sendCommand(disposeInspector("test.inspector")), SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void doubleInspectorCreation() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)),
                Status.ERROR);
        assertResponseStatus(serviceLayer.sendCommand(disposeInspector("test.inspector")), SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_DISPOSED);
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
    public void sendRawCommand() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommand(createInspector("test.inspector", onDevicePath)), SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        byte[] commandBytes = new byte[] {1, 2, 127};
        assertRawResponse(
                serviceLayer.sendCommand(rawCommandInspector("test.inspector", commandBytes)),
                commandBytes);
        assertInput(androidDriver, EXPECTED_INSPECTOR_PREFIX + Arrays.toString(commandBytes));
        List<AppInspectionEvent> events = serviceLayer.consumeCollectedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRawEvent().getContent().toByteArray())
                .isEqualTo(new byte[] {8, 92, 43});
    }

    private static AppInspectionCommand rawCommandInspector(
            String inspectorId, byte[] commandData) {
        return AppInspectionCommand.newBuilder()
                .setRawInspectorCommand(
                        RawCommand.newBuilder()
                                .setInspectorId(inspectorId)
                                .setContent(ByteString.copyFrom(commandData))
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

    private static void assertRawResponse(AppInspectionEvent event, byte[] responseContent) {
        assertThat(event.hasRawEvent()).isTrue();
        assertThat(event.getRawEvent().getContent().toByteArray()).isEqualTo(responseContent);
    }

    private static String injectInspectorDex() throws IOException {
        File inspectorDex = new File(ProcessRunner.getProcessPath("test.inspector.dex.location"));
        assertThat(inspectorDex.exists()).isTrue();
        String onDevicePath = "test_inspector.jar";
        Files.copy(inspectorDex.toPath(), new File(onDevicePath).toPath(), REPLACE_EXISTING);
        return onDevicePath;
    }

    private static void assertInput(FakeAndroidDriver androidDriver, String expected) {
        assertThat(androidDriver.waitForInput(expected, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)))
                .isTrue();
    }
}
