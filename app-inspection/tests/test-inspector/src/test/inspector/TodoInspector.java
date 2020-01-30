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
import androidx.inspection.Connection;
import androidx.inspection.InspectorEnvironment;
import java.util.*;
import test.inspector.api.TestInspectorApi;
import test.inspector.api.TodoInspectorApi;

/** An inspector that can provide hooks into the TodoActivity. */
public final class TodoInspector extends TestInspector {

    private final Class<?> classItem;
    private final Class<?> classGroup;

    TodoInspector(@NonNull Connection connection, @NonNull InspectorEnvironment environment) {
        super(connection, environment);

        try {
            classItem = forName("com.activity.todo.TodoItem");
            classGroup = forName("com.activity.todo.TodoGroup");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
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
