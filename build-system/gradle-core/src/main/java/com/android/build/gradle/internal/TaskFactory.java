/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;

/**
 * Interface for a container that can create Task.
 */
public interface TaskFactory {
    /** Returns true if this collection contains an item with the given name. */
    boolean containsKey(@NonNull String name);

    /**
     * Returns the {@link Task} named name from the current set of defined tasks.
     *
     * @param name the name of the requested {@link Task}
     * @return the {@link Task} instance or null if not found.
     */
    @Nullable
    Task findByName(@NonNull String name);

    /** Creates a task with the given name. */
    Task create(@NonNull String name);

    /** Creates a task with the given name and type. */
    <S extends Task> S create(@NonNull String name, @NonNull Class<S> type);

    /** Creates a task with the given {@link TaskConfigAction} */
    <T extends Task> T create(@NonNull TaskConfigAction<T> configAction);

    /** Creates a task wit the given name, type and configuration action */
    <T extends Task> T create(
            @NonNull String taskName, Class<T> taskClass, @NonNull Action<T> configAction);

    /** Creates a task with the given name and config action. */
    DefaultTask create(@NonNull String taskName, @NonNull Action<DefaultTask> configAction);

    /** Applies the given configAction to the task with given name. */
    void configure(@NonNull String name, @NonNull Action<? super Task> configAction);
}
