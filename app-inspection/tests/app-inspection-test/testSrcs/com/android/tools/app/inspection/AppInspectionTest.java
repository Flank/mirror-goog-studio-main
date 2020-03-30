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
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import com.android.tools.app.inspection.AppInspection.*;
import com.android.tools.app.inspection.AppInspection.AppInspectionResponse.Status;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.transport.device.SdkLevel;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import test.inspector.api.TestInspectorApi;
import test.inspector.api.TodoInspectorApi;

@RunWith(Parameterized.class)
public final class AppInspectionTest {
    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        // Enter/exit hook implementation slightly changes between O and P
        // findInstances has different implementation in Q
        return Lists.newArrayList(SdkLevel.O, SdkLevel.P, SdkLevel.Q);
    }

    private static final String TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String EXPECTED_INSPECTOR_PREFIX = "TEST INSPECTOR ";
    private static final String EXPECTED_INSPECTOR_CREATED = EXPECTED_INSPECTOR_PREFIX + "CREATED";
    private static final String EXPECTED_INSPECTOR_DISPOSED =
            EXPECTED_INSPECTOR_PREFIX + "DISPOSED";
    private static final String EXPECTED_INSPECTOR_COMMAND_PREFIX =
            EXPECTED_INSPECTOR_PREFIX + "COMMAND: ";

    @Rule public final AppInspectionRule appInspectionRule;

    private FakeAndroidDriver androidDriver;

    public AppInspectionTest(@NonNull SdkLevel level) {
        appInspectionRule = new AppInspectionRule(TODO_ACTIVITY, level);
    }

    @Before
    public void setUp() {
        androidDriver = appInspectionRule.transportRule.getAndroidDriver();
    }

    @Test
    public void createThenDispose() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void doubleInspectorCreation() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", onDevicePath)),
                ERROR);
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void disposeNonexistent() throws Exception {
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector("test.inspector")),
                ERROR);
    }

    @Test
    public void createFailsWithUnknownInspectorId() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(createInspector("foo", onDevicePath)),
                ERROR);
    }

    @Test
    public void createFailsIfInspectorDexIsNonexistent() throws Exception {
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector("test.inspector", "random_file")),
                ERROR);
    }

    @Test
    public void sendRawCommand() throws Exception {
        String onDevicePath = injectInspectorDex();
        assertResponseStatus(
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
        assertResponseStatus(
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
                "Inspector "
                        + inspectorId
                        + " crashed during sendCommand due to: This is an inspector exception.");
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_DISPOSED);
    }

    @Test
    public void tryToCreateExistingInspectorResultsInException() throws Exception {
        String inspectorId = "test.inspector";
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex(), "project.A")),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);
        AppInspectionResponse response =
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex(), "project.B"));
        assertThat(response.getStatus()).isEqualTo(ERROR);
        assertThat(response.getErrorMessage())
                .isEqualTo(
                        "Inspector with the given id "
                                + inspectorId
                                + " already exists. It was launched by project: project.A");
    }

    /**
     * The inspector framework includes features for finding object instances on the heap. This test
     * indirectly verifies it works.
     *
     * <p>See the {@code TodoInspector} in the test-inspector project for the relevant code.
     */
    @Test
    public void findInstancesWorks() throws Exception {
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");

        String inspectorId = "todo.inspector";
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        appInspectionRule.assertInput(EXPECTED_INSPECTOR_CREATED);

        // Ensure you can find object instances that were created before the inspector was attached.
        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_GROUPS.toByteArray())),
                new byte[] {(byte) 2});

        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_ITEMS.toByteArray())),
                new byte[] {(byte) 3});

        // Add more objects and ensure those instances get picked up as well
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem");

        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_GROUPS.toByteArray())),
                new byte[] {(byte) 4});

        assertRawResponse(
                appInspectionRule.sendCommandAndGetResponse(
                        rawCommandInspector(
                                inspectorId,
                                TodoInspectorApi.Command.COUNT_TODO_ITEMS.toByteArray())),
                new byte[] {(byte) 7});

        // This test generates a bunch of events, but we don't care about checking those here;
        // we'll look into those more in the following test.
        while (appInspectionRule.hasEventToCollect()) {
            appInspectionRule.consumeCollectedEvent();
        }
    }

    @Test
    public void enterAndExitHooksWork() throws Exception {
        String inspectorId = "todo.inspector";
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        // In order to generate a bunch of events, create a bunch of to-do items and groups.
        //
        // First, create a new item, which creates a default group as a side effect. This means we
        // will enter "newItem" first, then enter "newGroup", then exit "newGroup", then exit
        // "newItem"
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item #1 (and Group #1, indirectly)

        // Next, create misc groups and items
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group #2
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item #1
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item #2

        { // Item #1 enter
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Group #1 enter
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
        }

        { // Group #1 exit
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
        }

        { // Item #1 exit
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        { // Group #2 enter
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
        }

        { // Group #2 exit
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
        }

        { // Item #2 enter
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Item #2 exit
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        { // Item #3 enter
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Item #3 exit
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
    }

    @Test
    public void enterHookCanAlsoCaptureParameters() throws Exception {
        // Create a bunch of groups up front that we'll remove from in a little bit
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[1]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[2]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[3]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[4]

        String inspectorId = "todo.inspector";
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        // Remove groups - each method we call below calls "removeGroup(idx)"
        // indirectly, which triggers an event that includes the index.
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeNewestGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeOldestGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeNewestGroup");

        { // removeNewestGroup: Group[4] removed. Group[3] is now the last group
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GROUP_REMOVING.toByteArrayWithArg(
                                    (byte) 4));
        }

        { // removeOldestGroup: Group[0] removed. Group[2] is now the last group.
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GROUP_REMOVING.toByteArrayWithArg(
                                    (byte) 0));
        }

        { // removeNewestGroup: Group[2] removed.
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GROUP_REMOVING.toByteArrayWithArg(
                                    (byte) 2));
        }

        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
    }

    @Test
    public void exitHooksSupportVoidAndPrimitives() throws Exception {
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[1]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[3]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[4]

        String inspectorId = "todo.inspector";
        assertResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);
        androidDriver.triggerMethod(TODO_ACTIVITY, "getItemsCount");
        androidDriver.triggerMethod(TODO_ACTIVITY, "clearAllItems");
        { // getItemsCount
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_ITEMS_COUNT.toByteArrayWithArg(
                                    (byte) 1));
        }

        { // clearAllItems
            AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_CLEARED_ALL_ITEMS.toByteArray());
        }
    }

    @Test
    public void handleCancellationCommand() throws Exception {
        String inspectorId = "test.cancellation.inspector";
        assertResponseStatus(
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
    private static AppInspectionCommand cancellationCommand(int cancelledCommandId) {
        return AppInspectionCommand.newBuilder()
                .setCancellationCommand(
                        AppInspection.CancellationCommand.newBuilder()
                                .setCancelledCommandId(cancelledCommandId)
                                .build())
                .build();
    }

    @NonNull
    private static AppInspectionCommand createInspector(String inspectorId, String dexPath) {
        return createInspector(inspectorId, dexPath, "test.project");
    }

    @NonNull
    private static AppInspectionCommand createInspector(
            String inspectorId, String dexPath, String project) {
        return AppInspectionCommand.newBuilder()
                .setInspectorId(inspectorId)
                .setCreateInspectorCommand(
                        CreateInspectorCommand.newBuilder()
                                .setDexPath(dexPath)
                                .setLaunchMetadata(
                                        LaunchMetadata.newBuilder()
                                                .setLaunchedByName(project)
                                                .build())
                                .build())
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
}
