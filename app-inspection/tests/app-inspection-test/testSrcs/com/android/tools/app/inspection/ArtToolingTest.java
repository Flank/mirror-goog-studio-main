/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.app.inspection.AppInspection.CreateInspectorResponse.Status.SUCCESS;
import static com.android.tools.app.inspection.AppInspectionRule.injectInspectorDex;
import static com.android.tools.app.inspection.Asserts.*;
import static com.android.tools.app.inspection.Commands.*;
import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.transport.device.SdkLevel;
import com.google.common.collect.Lists;
import java.nio.ByteBuffer;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import test.inspector.api.TodoInspectorApi;

@RunWith(Parameterized.class)
public class ArtToolingTest {
    private static final String TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String EXPECTED_INSPECTOR_PREFIX = "TEST INSPECTOR ";
    private static final String EXPECTED_INSPECTOR_CREATED = EXPECTED_INSPECTOR_PREFIX + "CREATED";

    @Parameterized.Parameters
    public static Collection<SdkLevel> parameters() {
        // Enter/exit hook implementation slightly changes between O and P
        // findInstances has different implementation in Q
        return Lists.newArrayList(SdkLevel.O, SdkLevel.P, SdkLevel.Q);
    }

    @Rule public final AppInspectionRule appInspectionRule;

    private FakeAndroidDriver androidDriver;

    public ArtToolingTest(@NonNull SdkLevel level) {
        appInspectionRule = new AppInspectionRule(TODO_ACTIVITY, level);
    }

    @Before
    public void setUp() {
        androidDriver = appInspectionRule.transportRule.getAndroidDriver();
    }

    @Test
    public void enterAndExitHooksWork() throws Exception {
        String inspectorId = "todo.inspector";
        assertCreateInspectorResponseStatus(
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
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item #2
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item #3
        androidDriver.triggerMethod(TODO_ACTIVITY, "newInnerItem"); // Item #4

        { // Item #1 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Group #1 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
        }

        { // Group #1 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
        }

        { // Item #1 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        { // Group #2 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
        }

        { // Group #2 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
        }

        { // Item #2 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Item #2 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        { // Item #3 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Item #3 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        { // Item #4 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
        }

        { // Item #4 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
        }

        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
    }

    @Test
    public void enterHookCanAlsoCaptureParameters() throws Exception {
        // Create a bunch of groups up front that we'll remove from in a little bit
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item[0] in Group[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[1]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[2]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[3]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[4]

        String inspectorId = "todo.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        androidDriver.triggerMethod(TODO_ACTIVITY, "logFirstItem");

        // Remove groups - each method we call below calls "removeGroup(idx)"
        // indirectly, which triggers an event that includes the index.
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeNewestGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeOldestGroup");
        androidDriver.triggerMethod(TODO_ACTIVITY, "removeNewestGroup");

        { // logFirstItem
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_LOGGING_ITEM.toByteArrayWithArg(
                                    new byte[] {(byte) 1, (byte) 2}));
        }

        { // removeNewestGroup: Group[4] removed. Group[3] is now the last group
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GROUP_REMOVING.toByteArrayWithArg(
                                    (byte) 4));
        }

        { // removeOldestGroup: Group[0] removed. Group[2] is now the last group.
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GROUP_REMOVING.toByteArrayWithArg(
                                    (byte) 0));
        }

        { // removeNewestGroup: Group[2] removed.
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
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
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item[0]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[2]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group[3]
        androidDriver.triggerMethod(TODO_ACTIVITY, "newItem"); // Item[0]

        String inspectorId = "todo.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        androidDriver.triggerMethod(TODO_ACTIVITY, "getItemsCount");
        androidDriver.triggerMethod(TODO_ACTIVITY, "getByteItemsCount");
        androidDriver.triggerMethod(TODO_ACTIVITY, "getShortItemsCount");
        androidDriver.triggerMethod(TODO_ACTIVITY, "getLongItemsCount");

        androidDriver.triggerMethod(TODO_ACTIVITY, "getActiveGroupTrailingChar");

        androidDriver.triggerMethod(TODO_ACTIVITY, "getAverageItemCount");
        androidDriver.triggerMethod(TODO_ACTIVITY, "getDoubleAverageItemCount");

        androidDriver.triggerMethod(TODO_ACTIVITY, "hasEmptyTodoList");
        androidDriver.triggerMethod(TODO_ACTIVITY, "clearAllItems");
        androidDriver.triggerMethod(TODO_ACTIVITY, "hasEmptyTodoList");

        androidDriver.triggerMethod(TODO_ACTIVITY, "callEcho");
        { // getItemsCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_ITEMS_COUNT.toByteArrayWithArg(
                                    (byte) 3));
        }

        { // getByteItemsCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_BYTE_ITEMS_COUNT.toByteArrayWithArg(
                                    (byte) 3));
        }

        { // getShortItemsCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_SHORT_ITEMS_COUNT.toByteArrayWithArg(
                                    (byte) 3));
        }

        { // getLongItemsCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_LONG_ITEMS_COUNT.toByteArrayWithArg(
                                    (byte) 3));
        }

        { // getActiveGroupTrailingChar
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_GROUP_TRAILING_CHAR.toByteArrayWithArg(
                                    (byte) '4')); // "Group #4"
        }

        { // getAverageItemCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_AVERAGE_ITEMS_COUNT.toByteArrayWithArg(
                                    ByteBuffer.allocate(4)
                                            .putFloat(0.75f)
                                            .array())); // 3 items across four groups
        }

        { // getDoubleAverageItemCount
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_GOT_DOUBLE_AVERAGE_ITEMS_COUNT
                                    .toByteArrayWithArg(
                                            ByteBuffer.allocate(8).putDouble(0.75).array()));
        }

        { // hasEmptyTodoList
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_HAS_EMPTY_TODO_LIST.toByteArrayWithArg(
                                    (byte) 0));
        }

        { // clearAllItems
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_CLEARED_ALL_ITEMS.toByteArray());
        }

        { // hasEmptyTodoList
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_HAS_EMPTY_TODO_LIST.toByteArrayWithArg(
                                    (byte) 1));
        }
        { // callEcho
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ECHOED.toByteArray());
        }
    }

    @Test
    public void hooksWithHighRegisties() throws Exception {
        String inspectorId = "todo.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        androidDriver.triggerMethod(TODO_ACTIVITY, "prefillItems");

        { // prefillItems entry
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_ITEMS_PREFILLING.toByteArray());
        }

        { // prefillItems entry
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_ITEMS_PREFILLED.toByteArrayWithArg(
                                    (byte) 12));
        }

        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
    }

    @Test
    public void entryAndExitHooksDisposed() throws Exception {
        String inspectorId = "todo.inspector";
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        // doesn't fail
        androidDriver.triggerMethod(TODO_ACTIVITY, "selectFirstGroup");

        assertDisposeInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(disposeInspector(inspectorId)),
                AppInspection.AppInspectionResponse.Status.SUCCESS);

        assertThat(appInspectionRule.consumeCollectedEvent().hasDisposedEvent()).isTrue();

        // hooks will throw if they are called but inspection is disposed
        androidDriver.triggerMethod(TODO_ACTIVITY, "selectFirstGroup");
    }

    @Test
    public void entryAndExitDoubleHooks() throws Exception {
        String inspectorId = "todo.inspector";
        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup");
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        androidDriver.triggerMethod(TODO_ACTIVITY, "selectLastGroup");
        {
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTING.toByteArrayWithArg(
                                    (byte) 0));
        }
        {
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTING.toByteArrayWithArg(
                                    (byte) 1));
        }

        {
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTED.toByteArrayWithArg(
                                    (byte) 0));
        }

        {
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTED.toByteArrayWithArg(
                                    (byte) 1));
        }
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
        assertCreateInspectorResponseStatus(
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
        androidDriver.triggerMethod(TODO_ACTIVITY, "newRedItem");

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

    // TODO: b/159250979; Expand tests once it is correctly supported
    @Test
    public void exitHooksOverloadWork() throws Exception {
        String inspectorId = "todo.inspector";
        assertCreateInspectorResponseStatus(
                appInspectionRule.sendCommandAndGetResponse(
                        createInspector(inspectorId, injectInspectorDex())),
                SUCCESS);

        androidDriver.triggerMethod(TODO_ACTIVITY, "newGroup"); // Group #1
        androidDriver.triggerMethod(TODO_ACTIVITY, "newHighPriorityGroup"); // High Priority Group

        { // Group #1 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
        }

        { // Group #1 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
        }

        { // Group #2 enter
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(
                            TodoInspectorApi.Event.TODO_NAMED_GROUP_CREATING.toByteArrayWithArg(
                                    "High Priority Group".getBytes()));
        }

        { // Group #2 exit
            AppInspection.AppInspectionEvent event = appInspectionRule.consumeCollectedEvent();
            assertThat(event.getRawEvent().getContent().toByteArray())
                    .isEqualTo(TodoInspectorApi.Event.TODO_NAMED_GROUP_CREATED.toByteArray());
        }

        assertThat(appInspectionRule.hasEventToCollect()).isFalse();
    }
}
