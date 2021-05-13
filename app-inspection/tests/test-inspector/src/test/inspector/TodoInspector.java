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
import androidx.inspection.ArtTooling;
import androidx.inspection.ArtTooling.EntryHook;
import androidx.inspection.ArtTooling.ExitHook;
import androidx.inspection.Connection;
import androidx.inspection.InspectorEnvironment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import test.inspector.api.TestInspectorApi;
import test.inspector.api.TodoInspectorApi;

/** An inspector that can provide hooks into the TodoActivity. */
public final class TodoInspector extends TestInspector {
    private static final String CLASS_TODO_GROUP = "com.activity.todo.TodoGroup";
    private static final String CLASS_TODO_ITEM = "com.activity.todo.TodoItem";
    private static final String CLASS_TODO_INNER_ITEM = "com.activity.todo.TodoActivity$InnerItem";
    private static final String CLASS_TODO_ACTIVITY = "com.activity.todo.TodoActivity";
    private static final String CLASS_STRING = "java.lang.String";
    private static final String SIGNATURE_TODO_GROUP = toSignature(CLASS_TODO_GROUP);
    private static final String SIGNATURE_TODO_ITEM = toSignature(CLASS_TODO_ITEM);
    private static final String SIGNATURE_STRING = toSignature(CLASS_STRING);

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
    private final Class<?> classItemInner;
    private final Class<?> classGroup;
    private final Class<?> classActivity;
    private boolean isDisposed;

    TodoInspector(@NonNull Connection connection, @NonNull InspectorEnvironment environment) {
        super(connection, environment);

        try {
            classItem = forName(CLASS_TODO_ITEM);
            classItemInner = forName(CLASS_TODO_INNER_ITEM);
            classGroup = forName(CLASS_TODO_GROUP);
            classActivity = forName(CLASS_TODO_ACTIVITY);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        ArtTooling artTooling = environment.artTooling();
        artTooling.registerEntryHook(
                classItemInner,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
                    }
                });

        artTooling.registerExitHook(
                classItemInner,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
                        return returnValue;
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "newGroup()" + SIGNATURE_TODO_GROUP,
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GROUP_CREATING.toByteArray());
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "newGroup(" + SIGNATURE_STRING + ")" + SIGNATURE_TODO_GROUP,
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_NAMED_GROUP_CREATING
                                                .toByteArrayWithArg(
                                                        ((String) params.get(0)).getBytes()));
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "newGroup()" + SIGNATURE_TODO_GROUP,
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_GROUP_CREATED.toByteArray());
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "newGroup(" + SIGNATURE_STRING + ")" + SIGNATURE_TODO_GROUP,
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_NAMED_GROUP_CREATED
                                                .toByteArray());
                        return returnValue;
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATING.toByteArray());
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "newItem()" + SIGNATURE_TODO_ITEM,
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(TodoInspectorApi.Event.TODO_ITEM_CREATED.toByteArray());
                        return returnValue;
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "removeGroup(I)V",
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        Integer index = (Integer) params.get(0);
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GROUP_REMOVING
                                                .toByteArrayWithArg(index.byteValue()));
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "getItemsCount()I",
                new ExitHook<Object>() {
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

        artTooling.registerExitHook(
                classActivity,
                "getByteItemsCount()B",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Byte count = (Byte) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_BYTE_ITEMS_COUNT
                                                .toByteArrayWithArg(count));
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "getShortItemsCount()S",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Short count = (Short) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_SHORT_ITEMS_COUNT
                                                .toByteArrayWithArg(count.byteValue()));
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "getLongItemsCount()J",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Long count = (Long) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_LONG_ITEMS_COUNT
                                                .toByteArrayWithArg(count.byteValue()));
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "clearAllItems()V",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_CLEARED_ALL_ITEMS
                                                .toByteArray());
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "hasEmptyTodoList()Z",
                new ExitHook<Object>() {
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

        artTooling.registerExitHook(
                classActivity,
                "getActiveGroupTrailingChar()C",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Character trailing = (Character) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_GROUP_TRAILING_CHAR
                                                .toByteArrayWithArg((byte) trailing.charValue()));
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "getAverageItemCount()F",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Float avg = (Float) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_AVERAGE_ITEMS_COUNT
                                                .toByteArrayWithArg(
                                                        ByteBuffer.allocate(4)
                                                                .putFloat(avg)
                                                                .array()));
                        return returnValue;
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "getDoubleAverageItemCount()D",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        Double avg = (Double) returnValue;
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_GOT_DOUBLE_AVERAGE_ITEMS_COUNT
                                                .toByteArrayWithArg(
                                                        ByteBuffer.allocate(8)
                                                                .putDouble(avg)
                                                                .array()));
                        return returnValue;
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "prefillItems()I",
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_ITEMS_PREFILLING.toByteArray());
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "prefillItems()I",
                new ExitHook<Integer>() {
                    @Override
                    public Integer onExit(@NonNull Integer returnValue) {
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_ITEMS_PREFILLED
                                                .toByteArrayWithArg(returnValue.byteValue()));
                        return returnValue;
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "logItem(I" + SIGNATURE_STRING + SIGNATURE_TODO_ITEM + ")V",
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        byte s = self == null ? (byte) 1 : (byte) 0;
                        Integer severity = (Integer) params.get(0);
                        byte[] args = new byte[] {s, severity.byteValue()};
                        getConnection()
                                .sendEvent(
                                        TodoInspectorApi.Event.TODO_LOGGING_ITEM.toByteArrayWithArg(
                                                args));
                    }
                });

        artTooling.registerEntryHook(
                classActivity,
                "selectFirstGroup()V",
                new EntryHook() {
                    @Override
                    public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                        if (isDisposed) {
                            throw new AssertionError(
                                    "entry hook shouldn't be called after onDipose");
                        }
                    }
                });

        artTooling.registerExitHook(
                classActivity,
                "selectFirstGroup()V",
                new ExitHook<Object>() {
                    @Override
                    public Object onExit(Object returnValue) {
                        if (isDisposed) {
                            throw new AssertionError(
                                    "exit hook shouldn't be called after onDipose");
                        }
                        return returnValue;
                    }
                });

        for (byte i = 0; i < 2; i++) {
            final byte hookId = i;
            artTooling.registerEntryHook(
                    classActivity,
                    "selectLastGroup()V",
                    new EntryHook() {
                        @Override
                        public void onEntry(@Nullable Object self, @NonNull List<Object> params) {
                            getConnection()
                                    .sendEvent(
                                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTING
                                                    .toByteArrayWithArg(hookId));
                        }
                    });

            artTooling.registerExitHook(
                    classActivity,
                    "selectLastGroup()V",
                    new ExitHook<Object>() {
                        @Override
                        public Object onExit(Object returnValue) {
                            getConnection()
                                    .sendEvent(
                                            TodoInspectorApi.Event.TODO_LAST_ITEM_SELECTED
                                                    .toByteArrayWithArg(hookId));
                            return returnValue;
                        }
                    });
        }

        artTooling.registerExitHook(
                classActivity,
                "echo(J)J",
                new ExitHook<Long>() {
                    @Override
                    public Long onExit(Long result) {
                        getConnection().sendEvent(TodoInspectorApi.Event.TODO_ECHOED.toByteArray());
                        return result;
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
                        return new byte[] {
                            (byte) environment.artTooling().findInstances(classGroup).size()
                        };
                    case COUNT_TODO_ITEMS:
                        return new byte[] {
                            (byte) environment.artTooling().findInstances(classItem).size()
                        };
                }
                break;
            }
        }

        return TestInspectorApi.Reply.ERROR.toByteArray();
    }

    @Override
    protected void handleDispose() {
        isDisposed = true;
    }
}
