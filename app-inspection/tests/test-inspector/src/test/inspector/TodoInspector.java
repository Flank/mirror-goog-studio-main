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

package test.inspector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.inspection.Connection;
import androidx.inspection.InspectorEnvironment;
import java.util.*;
import test.inspector.api.TestInspectorApi;
import test.inspector.api.TodoInspectorApi;

/** An inspector that can provide hooks into the TodoActivity. */
public final class TodoInspector extends TestInspector {
    private static final String CLASS_TODO_GROUP = "com.activity.todo.TodoGroup";
    private static final String CLASS_TODO_ITEM = "com.activity.todo.TodoItem";
    private static final String CLASS_TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String SIGNATURE_TODO_GROUP = toSignature(CLASS_TODO_GROUP);
    private static final String SIGNATURE_TODO_ITEM = toSignature(CLASS_TODO_ITEM);

    /**
     * Convert a fully qualified class name to the Java Byte Code syntax
     *
     * <p>For example, "com.example.pkg.SomeClass" -> "Lcom/example/pkg/SomeClass;"
     */
    @NonNull
    private static String toSignature(@NonNull String fqcn) {
        return "L" + fqcn.replace(".", "/") + ";";
    }

    private final Class<?> classItem;
    private final Class<?> classGroup;
    private final Class<?> classActivity;

    TodoInspector(@NonNull Connection connection, @NonNull InspectorEnvironment environment) {
        super(connection, environment);

        try {
            classItem = forName(CLASS_TODO_ITEM);
            classGroup = forName(CLASS_TODO_GROUP);
            classActivity = forName(CLASS_TODO_ACTIVITY);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        environment.registerEntryHook(
                classActivity,
                "newGroup()" + SIGNATURE_TODO_GROUP,
                new InspectorEnvironment.EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
                    }
                });

        environment.registerExitHook(
                classActivity,
                "newGroup()" + SIGNATURE_TODO_GROUP,
                new InspectorEnvironment.ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
                        return returnValue;
                    }
                });

        environment.registerEntryHook(
                classActivity,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new InspectorEnvironment.EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
                    }
                });

        environment.registerExitHook(
                classActivity,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new InspectorEnvironment.ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
                        return returnValue;
                    }
                });

        environment.registerEntryHook(
                classActivity,
                "removeGroup(I)V",
                new InspectorEnvironment.EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        Integer index = (Integer) params.get(0);
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GROUP_REMOVING
                                                .toByteArrayWithArg(index.byteValue()));
                    }
                });

        environment.registerExitHook(
                classActivity,
                "getItemsCount()I",
                new InspectorEnvironment.ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Integer count = (Integer) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_ITEMS_COUNT
                                                .toByteArrayWithArg(count.byteValue()));
                        return returnValue;
                    }
                });

        environment.registerExitHook(
                classActivity,
                "clearAllItems()V",
                new InspectorEnvironment.ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_CLEARED_ALL_ITEMS
                                                .toByteArray());
                        return returnValue;
                    }
                });

        environment.registerExitHook(
                classActivity,
                "hasEmptyTodoList()Z",
                new InspectorEnvironment.ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Boolean empty = (Boolean) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_HAS_EMPTY_TODO_LIST
                                                .toByteArrayWithArg((byte) (empty ? 1 : 0)));
                        return returnValue;
                    }
                });
    }

    @NonNull
    @Override
    protected byte[] handleReceiveCommand(@NonNull byte[] bytes) {
        for (TodoInspectorApi.Command command : TodoInspectorApi.Command.values()) {
            if (Arrays.equals(command.toByteArray(), bytes)) {
                switch (command) {
                    case COUNT_TODO_GROUPS:
                        return new byte[] {(byte) environment.findInstances(classGroup).size()};
                    case COUNT_TODO_ITEMS:
                        return new byte[] {(byte) environment.findInstances(classItem).size()};
                }
                break;
            }
        }

        return TestInspectorApi.Reply.ERROR.toByteArray();
    }
}
