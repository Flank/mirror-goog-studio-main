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
import static com.android.tools.app.inspection.AppInspection.CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR;
import static com.android.tools.app.inspection.AppInspection.CreateInspectorResponse.Status.LIBRARY_MISSING;
import static com.android.tools.app.inspection.AppInspection.CreateInspectorResponse.Status.SUCCESS;
import static com.android.tools.app.inspection.AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE;
import static com.android.tools.app.inspection.AppInspectionRule.injectInspectorDex;
import static com.android.tools.app.inspection.Asserts.assertCreateInspectorResponseStatus;
import static com.android.tools.app.inspection.Asserts.assertDisposeInspectorResponseStatus;
import static com.android.tools.app.inspection.Asserts.assertRawResponse;
import static com.android.tools.app.inspection.Commands.createInspector;
import static com.android.tools.app.inspection.Commands.createLibraryInspector;
import static com.android.tools.app.inspection.Commands.disposeInspector;
import static com.android.tools.app.inspection.Commands.rawCommandInspector;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse;
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status;
import com.android.tools.app.inspection.AppInspection.LaunchMetadata;
import com.android.tools.transport.device.SdkLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import test.inspector.api.NoReplyInspectorApi;
import test.inspector.api.PayloadInspectorApi;
import test.inspector.api.TestExecutorsApi;
import test.inspector.api.TestInspectorApi;

public final class AppInspectionTest {
    private static final String TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String EXPECTED_INSPECTOR_PREFIX = "TEST INSPECTOR ";
    private static final String EXPECTED_INSPECTOR_CREATED = EXPECTED_INSPECTOR_PREFIX + "CREATED";
    private static final String EXPECTED_INSPECTOR_DISPOSED =
            EXPECTED_INSPECTOR_PREFIX + "DISPOSED";
    private static final String EXPECTED_INSPECTOR_COMMAND_PREFIX =
            EXPECTED_INSPECTOR_PREFIX + "COMMAND: ";

    @Rule
    public final AppInspectionRule appInspectionRule =
            new AppInspectionRule(TODO_ACTIVITY, SdkLevel.Q);

    @Test
    public void createThenDispose() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                Status.SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();
    }

    @Test
    public void createNativeThenRestart() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.native.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        appInspectionRule.assertInput("Native method result 541");
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        disposeInspector("test.native.inspector")),
                Status.SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();
        // check that it can be successfully restarted
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.native.inspector", onDevicePath)),
                SUCCESS);
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        disposeInspector("test.native.inspector")),
                Status.SUCCESS);
        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();
    }

    @Test
    public void doubleInspectorCreation() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                GENERIC_SERVICE_ERROR);
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                Status.SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();
    }

    @Test
    public void disposeNonexistent() throws Exception {
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                ERROR);
    }

    @Test
    public void createFailsWithUnknownInspectorId() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(createInspector("foo", onDevicePath)),
                GENERIC_SERVICE_ERROR);
    }

    @Test
    public void createFailsIfInspectorDexIsNonexistent() throws Exception {
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", "random_file")),
                GENERIC_SERVICE_ERROR);
    }

    @Test
    public void sendRawCommand() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("reverse.echo.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        byte[] commandBytes = new byte[] {1, 2, 127};
        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector("reverse.echo.inspector", commandBytes)),
                TestInspectorApi.Reply.SUCCESS.toByteArray());
        appInspectionRule.assertInput(
                EXPECTED_INSPECTOR_COMMAND_PREFIX + Arrays.toString(commandBytes));
        AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
        assertThat(event.getRawEvent().getContent().toByteArray())
                .isEqualTo(new byte[] {127, 2, 1});
    }

    @Test
    public void handleInspectorCrashDuringSendCommand() throws Exception {
        String inspectorId = "test.exception.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        byte[] commandBytes = new byte[] {1, 2, 127};
        appInspectionRule.sendCommand(rawCommandInspector(inspectorId, commandBytes));
        AppInspection.AppInspectionEvent crashEvent = appInspectionRule.consumeCollectedEvent();
        assertThat(appInspectionRule.hasEventToCollect()).isFalse();

        assertCrashEvent(
                crashEvent,
                inspectorId,
                "Inspector " + inspectorId + " crashed due to: This is an inspector exception.");
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void tryToCreateExistingInspectorResultsInException() throws Exception {
        String inspectorId = "test.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(
                                inspectorId, injectInspectorDex(), launchMetadata("project.A"))),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);

        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(
                                inspectorId, injectInspectorDex(), launchMetadata("project.B")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage())
                .startsWith(
                        "Inspector with the given id "
                                + inspectorId
                                + " already exists. It was launched by project: project.A");

        // If creation by force is requested, an exception will not be thrown
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(
                                inspectorId,
                                injectInspectorDex(),
                                launchMetadata("project.A", true))),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);

        AppInspectionEvent disposedEvent = appInspectionRule.consumeCollectedEvent();
        assertThat(disposedEvent.hasDisposedEvent()).isTrue();
        assertThat(disposedEvent.getDisposedEvent().getErrorMessage()).isEmpty();
    }

    private static LaunchMetadata launchMetadata(String projectName) {
        return launchMetadata(projectName, false);
    }

    private static LaunchMetadata launchMetadata(String projectName, boolean force) {
        return LaunchMetadata.newBuilder().setLaunchedByName(projectName).setForce(force).build();
    }

    @Test
    public void sendCommandToNonExistentInspector() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                Status.SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();
        byte[] commandBytes = new byte[] {1, 2, 127};
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector("test.inspector", commandBytes));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage())
                .isEqualTo("Inspector with id test.inspector wasn't previously created");
    }

    @Test
    public void handleCancellationCommand() throws Exception {
        String inspectorId = "test.cancellation.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        byte[] commandBytes = new byte[] {1, 2, 127};
        int firstCommandId =
                appInspectionRule.sendCommand(rawCommandInspector(inspectorId, commandBytes));
        int secondCommand =
                appInspectionRule.sendCommand(rawCommandInspector(inspectorId, commandBytes));
        appInspectionRule.assertInput("command #1 arrived");
        appInspectionRule.assertInput("command #2 arrived");
        appInspectionRule.sendCommand(cancellationCommand(secondCommand));
        appInspectionRule.assertInput("first executor: cancellation #1 for command #2");
        appInspectionRule.assertInput("second executor: cancellation #2 for command #2");
        appInspectionRule.assertInput("post cancellation executor: cancellation #3 for command #2");
        int thirdCommand =
                appInspectionRule.sendCommand(rawCommandInspector(inspectorId, commandBytes));
        appInspectionRule.sendCommand(cancellationCommand(thirdCommand));
        appInspectionRule.assertInput("command #3 arrived");
        appInspectionRule.assertInput("first executor: cancellation #1 for command #3");
        appInspectionRule.assertInput("second executor: cancellation #2 for command #3");
        appInspectionRule.assertInput("post cancellation executor: cancellation #3 for command #3");
        appInspectionRule.sendCommand(cancellationCommand(firstCommandId));
        appInspectionRule.assertInput("first executor: cancellation #1 for command #1");
        appInspectionRule.assertInput("second executor: cancellation #2 for command #1");
        appInspectionRule.assertInput("post cancellation executor: cancellation #3 for command #1");
    }

    @Test
    public void testInspectorExecutorsHandler() throws Exception {
        testInspectorExecutors(
                TestExecutorsApi.Command.COMPLETE_ON_HANDLER,
                TestExecutorsApi.Command.FAIL_ON_HANDLER);
    }

    @Test
    public void testInspectorExecutorsPrimary() throws Exception {
        testInspectorExecutors(
                TestExecutorsApi.Command.COMPLETE_ON_PRIMARY_EXECUTOR,
                TestExecutorsApi.Command.FAIL_ON_PRIMARY_EXECUTOR);
    }

    @Test
    public void testInspectorExecutorsIO() throws Exception {
        testInspectorExecutors(
                TestExecutorsApi.Command.COMPLETE_ON_IO, TestExecutorsApi.Command.FAIL_ON_IO);
    }

    private void testInspectorExecutors(
            TestExecutorsApi.Command completeCommand, TestExecutorsApi.Command failCommand)
            throws Exception {
        String inspectorId = "test.executors.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        appInspectionRule.sendCommand(
                rawCommandInspector(inspectorId, completeCommand.toByteArray()));

        AppInspectionEvent handlerCompleted = appInspectionRule.consumeCollectedEvent();
        assertThat(handlerCompleted.getRawEvent().getContent().toByteArray())
                .isEqualTo(TestExecutorsApi.Event.COMPLETED.toByteArray());

        appInspectionRule.sendCommand(rawCommandInspector(inspectorId, failCommand.toByteArray()));

        AppInspection.AppInspectionEvent crashEvent = appInspectionRule.consumeCollectedEvent();
        assertThat(appInspectionRule.hasEventToCollect()).isFalse();

        assertCrashEvent(
                crashEvent,
                inspectorId,
                "Inspector " + inspectorId + " crashed due to: This is an inspector exception.");
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void ifInspectorFailsToReplyToCommandCallbackItCrashes() throws Exception {
        String inspectorId = "test.no.reply.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        int noReplyCommand =
                appInspectionRule.sendCommand(
                        rawCommandInspector(
                                inspectorId,
                                NoReplyInspectorApi.Command.LOG_AND_NO_REPLY.toByteArray()));
        appInspectionRule.assertInput("Command received");
        appInspectionRule.sendCommandAndGetResponse(
                rawCommandInspector(inspectorId, NoReplyInspectorApi.Command.RUN_GC.toByteArray()));
        appInspectionRule.assertInput("Garbage collected");

        AppInspection.AppInspectionEvent crashEvent = appInspectionRule.consumeCollectedEvent();
        assertThat(appInspectionRule.hasEventToCollect()).isFalse();

        assertCrashEvent(
                crashEvent,
                inspectorId,
                "Inspector "
                        + inspectorId
                        + " crashed due to: "
                        + "CommandCallback#reply for command with ID "
                        + noReplyCommand
                        + " was never called");
    }

    @Test
    public void createLibraryInspectorSuccessfully() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.inspector",
                                onDevicePath,
                                artifactCoordinate("androidx.unreal", "unreal-unengine", "0.0.1"))),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
    }

    @Test
    public void createInspectorWithIncompatibleVersion() throws Exception {
        String onDevicePath = injectInspectorDex();
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.inspector",
                                onDevicePath,
                                artifactCoordinate("test.library", "test", "3.0.0")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getCreateInspectorResponse().getStatus())
                .isEqualTo(AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE);
    }

    @Test
    public void createInspectorInProguardedApp() throws Exception {
        String onDevicePath = injectInspectorDex();
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.proguard.inspector",
                                onDevicePath,
                                artifactCoordinate("test.library", "test", "0.0.1")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getCreateInspectorResponse().getStatus())
                .isEqualTo(AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED);
    }

    private static AppInspection.ArtifactCoordinate artifactCoordinate(
            String groupId, String artifactId, String version) {
        return AppInspection.ArtifactCoordinate.newBuilder()
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .setVersion(version)
                .build();
    }

    @Test
    public void createInspectorWithInvalidVersionInput() throws Exception {
        String onDevicePath = injectInspectorDex();
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.inspector",
                                onDevicePath,
                                artifactCoordinate("test.library", "test", "3.a")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage())
                .isEqualTo("Failed to parse provided min version 3.a");
        assertThat(response.getCreateInspectorResponse().getStatus())
                .isEqualTo(GENERIC_SERVICE_ERROR);
    }

    @Test
    public void createInspectorWithNonExistentVersionFile() throws Exception {
        String onDevicePath = injectInspectorDex();
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.inspector",
                                onDevicePath,
                                artifactCoordinate("non", "existent", "1.0.0")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage()).startsWith("Failed to find version file");
        assertThat(response.getCreateInspectorResponse().getStatus()).isEqualTo(LIBRARY_MISSING);
    }

    @Test
    public void createInspectorWithInvalidVersionFile() throws Exception {
        String onDevicePath = injectInspectorDex();
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createLibraryInspector(
                                "test.inspector",
                                onDevicePath,
                                artifactCoordinate("test.invalid", "test", "1.0.0")));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage()).startsWith("Failed to parse version string");
        assertThat(response.getCreateInspectorResponse().getStatus())
                .isEqualTo(VERSION_INCOMPATIBLE);
    }

    @Test
    public void getLibraryCompatibilityInfoCommand() throws Exception {
        List<AppInspection.ArtifactCoordinate> artifacts = new ArrayList<>();
        // SUCCESS
        artifacts.add(artifactCoordinate("androidx.unreal", "unreal-unengine", "1.0.0"));
        // NOT FOUND
        artifacts.add(artifactCoordinate("non", "existent", "1.0.0"));
        // SERVICE ERROR
        artifacts.add(artifactCoordinate("test.invalid", "test", "1.0.0"));
        // INCOMPATIBLE
        artifacts.add(artifactCoordinate("test.library", "test", "2.0.0"));
        // INVALID MIN VERSION
        artifacts.add(artifactCoordinate("test.library", "test", "1232ad"));
        // APP_PROGUARDED
        artifacts.add(artifactCoordinate("test.library", "test", "1.0.0"));

        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        getLibraryCompatibilityInfoCommand(artifacts));
        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponsesCount())
                .isEqualTo(artifacts.size());

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(0).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.COMPATIBLE);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(0).getTargetLibrary())
                .isEqualTo(artifacts.get(0));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(0).getVersion())
                .isEqualTo("1.0.0");

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(1).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.LIBRARY_MISSING);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(1).getTargetLibrary())
                .isEqualTo(artifacts.get(1));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(1).getErrorMessage())
                .startsWith("Failed to find version file");
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(1).getVersion())
                .isEmpty();

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(2).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(2).getTargetLibrary())
                .isEqualTo(artifacts.get(2));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(2).getErrorMessage())
                .startsWith("Failed to parse version string");
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(2).getVersion())
                .isEqualTo("abc");

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(3).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.INCOMPATIBLE);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(3).getTargetLibrary())
                .isEqualTo(artifacts.get(3));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(3).getVersion())
                .isEqualTo("1.0.0");

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(4).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.SERVICE_ERROR);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(4).getTargetLibrary())
                .isEqualTo(artifacts.get(4));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(4).getErrorMessage())
                .startsWith("Failed to parse provided min version");
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(4).getVersion())
                .isEqualTo("1.0.0");

        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(5).getStatus())
                .isEqualTo(AppInspection.LibraryCompatibilityInfo.Status.APP_PROGUARDED);
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(5).getTargetLibrary())
                .isEqualTo(artifacts.get(5));
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(5).getErrorMessage())
                .startsWith("Proguard run was detected on the inspected app");
        assertThat(response.getGetLibraryCompatibilityResponse().getResponses(5).getVersion())
                .isEqualTo("1.0.0");
    }

    @Test
    public void receiveLargePayloadsInChunks() throws Exception {
        String onDevicePath = injectInspectorDex();
        String inspectorId = "payload.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);

        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                PayloadInspectorApi.Command.SEND_LARGE_RESPONSE.toByteArray()));
        {
            List<Byte> payload =
                    appInspectionRule.removePayload(response.getRawResponse().getPayloadId());

            assertThat(payload).hasSize(1024 * 1024);
            for (int i = 0; i < payload.size(); i++) {
                assertThat(payload.get(i)).isEqualTo((byte) i);
            }
        }

        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                PayloadInspectorApi.Command.SEND_LARGE_EVENT.toByteArray())),
                TestInspectorApi.Reply.SUCCESS.toByteArray());
        AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
        {
            List<Byte> payload =
                    appInspectionRule.removePayload(event.getRawEvent().getPayloadId());

            assertThat(payload).hasSize(1024 * 1024);
            for (int i = 0; i < payload.size(); i++) {
                assertThat(payload.get(i)).isEqualTo((byte) i);
            }
        }
    }

    @NonNull
    private static AppInspectionCommand cancellationCommand(int cancelledCommandId) {
        return AppInspectionCommand.newBuilder()
                .setCancellationCommand(
                        AppInspection.CancellationCommand.newBuilder()
                                .setCancelledCommandId(cancelledCommandId)
                                .build())
                .build();
    }

    @NonNull
    private static AppInspectionCommand getLibraryCompatibilityInfoCommand(
            List<AppInspection.ArtifactCoordinate> targetVersions) {
        return AppInspectionCommand.newBuilder()
                .setGetLibraryCompatibilityInfoCommand(
                        AppInspection.GetLibraryCompatibilityInfoCommand.newBuilder()
                                .addAllTargetLibraries(targetVersions))
                .build();
    }

    private static void assertCrashEvent(
            @NonNull AppInspectionEvent event,
            @NonNull String inspectorId,
            @NonNull String message) {
        assertThat(event.hasDisposedEvent()).isTrue();
        assertThat(event.getInspectorId()).isEqualTo(inspectorId);
        assertThat(event.getDisposedEvent().getErrorMessage()).isEqualTo(message);
    }
}
