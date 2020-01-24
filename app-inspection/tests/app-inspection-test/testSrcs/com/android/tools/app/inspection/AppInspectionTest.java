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

package com.android.tools.app.inspection;

import static com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.ERROR;
import static com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status.SUCCESS;
import static com.android.tools.app.inspection.ServiceLayer.TIMEOUT_SECONDS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import androidx.annotation.NonNull;
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status;
import com.android.tools.app.inspection.AppInspection.CreateInspectorCommand;
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand;
import com.android.tools.app.inspection.AppInspection.RawCommand;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Common;
import com.android.tools.transport.TransportRule;
import com.android.tools.transport.device.SdkLevel;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import test.inspector.api.TestInspectorApi;
import test.inspector.api.TodoInspectorApi;

public final class AppInspectionTest {
    private static final String TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String EXPECTED_INSPECTOR_PREFIX = "TEST INSPECTOR ";
    private static final String EXPECTED_INSPECTOR_CREATED = EXPECTED_INSPECTOR_PREFIX + "CREATED";
    private static final String EXPECTED_INSPECTOR_DISPOSED =
            EXPECTED_INSPECTOR_PREFIX + "DISPOSED";
    private static final String EXPECTED_INSPECTOR_COMMAND_PREFIX =
            EXPECTED_INSPECTOR_PREFIX + "COMMAND: ";

    @Rule public final TransportRule transportRule;
    @Rule public final Timeout timeout = new Timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    private ServiceLayer serviceLayer;
    private FakeAndroidDriver androidDriver;

    public AppInspectionTest() {
        TransportRule.Config ruleConfig =
                new TransportRule.Config() {
                    @Override
                    public void initDaemonConfig(
                            @NonNull Common.CommonConfig.Builder daemonConfig) {
                        daemonConfig.setProfilerUnifiedPipeline(true);
                    }
                };
        transportRule = new TransportRule(TODO_ACTIVITY, SdkLevel.O, ruleConfig);
    }

    @Before
    public void setUp() {
        androidDriver = transportRule.getAndroidDriver();
        serviceLayer = ServiceLayer.create(transportRule);
    }

    @After
    public void tearDown() {
        serviceLayer.shutdown();
    }

    @Test
    public void createThenDispose() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void doubleInspectorCreation() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                ERROR);
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void disposeNonexistent() throws Exception {
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(disposeInspector("test.inspector")), ERROR);
    }

    @Test
    public void createFailsWithUnknownInspectorId() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(createInspector("foo", onDevicePath)),
                ERROR);
    }

    @Test
    public void createFailsIfInspectorDexIsNonexistent() throws Exception {
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("test.inspector", "random_file")),
                ERROR);
    }

    @Test
    public void sendRawCommand() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("reverse.echo.inspector", onDevicePath)),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        byte[] commandBytes = new byte[] {1, 2, 127};
        assertRawResponse(
                serviceLayer.sendCommandAndGetResponse(
                        rawCommandInspector("reverse.echo.inspector", commandBytes)),
                TestInspectorApi.Reply.SUCCESS.toByteArray());
        assertInput(
                androidDriver, EXPECTED_INSPECTOR_COMMAND_PREFIX + Arrays.toString(commandBytes));
        AppInspection.AppInspectionEvent event = serviceLayer.consumeCollectedEvent();
        assertThat(serviceLayer.hasEventToCollect()).isFalse();
        assertThat(event.getRawEvent().getContent().toByteArray())
                .isEqualTo(new byte[] {127, 2, 1});
    }

    @Test
    public void handleInspectorCrashDuringSendCommand() throws Exception {
        String inspectorId = "test.exception.inspector";
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);
        byte[] commandBytes = new byte[] {1, 2, 127};
        serviceLayer.sendCommand(rawCommandInspector(inspectorId, commandBytes));
        AppInspection.AppInspectionEvent crashEvent = serviceLayer.consumeCollectedEvent();
        assertThat(serviceLayer.hasEventToCollect()).isFalse();

        assertCrashEvent(
                crashEvent,
                inspectorId,
                "Inspector "
                        + inspectorId
                        + " crashed during sendCommand due to: This is an inspector exception.");
        assertInput(androidDriver, EXPECTED_INSPECTOR_DISPOSED);
    }

    private static void assumeExperimentalFlag(boolean value) {
        assume().that(
                        Boolean.parseBoolean(
                                System.getProperty("app.inspection.experimental", "false")))
                .isEqualTo(value);
    }

    // TODO(b/145807005): Remove flag and delete this test
    @Test
    public void inspectorEnvironmentNoOps_WhenExperimentalFlagDisabled() throws Exception {
        assumeExperimentalFlag(false);
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector("test.environment.inspector", onDevicePath)),
                SUCCESS);
        assertInput(androidDriver, "FIND INSTANCES NOT IMPLEMENTED");
        assertInput(androidDriver, "REGISTER ENTRY HOOK NOT IMPLEMENTED");
        assertInput(androidDriver, "REGISTER EXIT HOOK NOT IMPLEMENTED");
    }

    /**
     * The inspector framework includes features for finding object instances on the heap. This test
     * indirectly verifies it works.
     *
     * <p>See the {@code TodoInspector} in the test-inspector project for the relevant code.
     */
    @Test
    public void findInstancesWorks_WhenExperimentalFlagEnabled() throws Exception {
        assumeExperimentalFlag(true);

        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newGroup");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newGroup");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");

        String inspectorId = "todo.inspector";
        assertResponseStatus(
                serviceLayer.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        assertInput(androidDriver, EXPECTED_INSPECTOR_CREATED);

        // Ensure you can find object instances that were created before the inspector was attached.
        assertRawResponse(
                serviceLayer.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_GROUPS.toByteArray())),
                new byte[] {(byte) 2});

        assertRawResponse(
                serviceLayer.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_ITEMS.toByteArray())),
                new byte[] {(byte) 3});

        // Add more objects and ensure those instances get picked up as well
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newGroup");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newGroup");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");
        transportRule.getAndroidDriver().triggerMethod(TODO_ACTIVITY, "newItem");

        assertRawResponse(
                serviceLayer.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_GROUPS.toByteArray())),
                new byte[] {(byte) 4});

        assertRawResponse(
                serviceLayer.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_ITEMS.toByteArray())),
                new byte[] {(byte) 7});
    }

    @Test
    public void enterAndExitHooksWork_WhenExperimentalFlagEnabled() throws Exception {
        assumeExperimentalFlag(true);
        // TODO(b/145807282): Implement transformation test
    }

    @NonNull
    private static AppInspectionCommand rawCommandInspector(
            @NonNull String inspectorId, @NonNull byte[] commandData) {
        return AppInspectionCommand.newBuilder()
                .setInspectorId(inspectorId)
                .setRawInspectorCommand(
                        RawCommand.newBuilder()
                                .setContent(ByteString.copyFrom(commandData))
                                .build())
                .build();
    }

    @NonNull
    private static AppInspectionCommand createInspector(String inspectorId, String dexPath) {
        return AppInspectionCommand.newBuilder()
                .setInspectorId(inspectorId)
                .setCreateInspectorCommand(
                        CreateInspectorCommand.newBuilder().setDexPath(dexPath).build())
                .build();
    }

    @NonNull
    private static AppInspectionCommand disposeInspector(@NonNull String inspectorId) {
        return AppInspectionCommand.newBuilder()
                .setInspectorId(inspectorId)
                .setDisposeInspectorCommand(DisposeInspectorCommand.newBuilder().build())
                .build();
    }

    private static void assertResponseStatus(
            @NonNull AppInspection.AppInspectionResponse response, @NonNull Status expected) {
        assertThat(response.hasServiceResponse()).isTrue();
        assertThat(response.getStatus()).isEqualTo(expected);
    }

    private static void assertCrashEvent(
            @NonNull AppInspectionEvent event,
            @NonNull String inspectorId,
            @NonNull String message) {
        assertThat(event.hasCrashEvent()).isTrue();
        assertThat(event.getInspectorId()).isEqualTo(inspectorId);
        assertThat(event.getCrashEvent().getErrorMessage()).isEqualTo(message);
    }

    private static void assertRawResponse(
            @NonNull AppInspection.AppInspectionResponse response, byte[] responseContent) {
        assertThat(response.hasRawResponse()).isTrue();
        assertThat(response.getRawResponse().getContent().toByteArray()).isEqualTo(responseContent);
    }

    @NonNull
    private static String injectInspectorDex() {
        File onHostinspector = new File(ProcessRunner.getProcessPath("test.inspector.dex.location"));
        assertThat(onHostinspector.exists()).isTrue();
        File onDeviceInspector = new File(onHostinspector.getName());
        // Should have already been copied over by the underlying transport test framework
        assertThat(onDeviceInspector.exists()).isTrue();
        return onDeviceInspector.getAbsolutePath();
    }

    private static void assertInput(
            @NonNull FakeAndroidDriver androidDriver, @NonNull String expected) {
        assertThat(androidDriver.waitForInput(expected)).isTrue();
    }
}
