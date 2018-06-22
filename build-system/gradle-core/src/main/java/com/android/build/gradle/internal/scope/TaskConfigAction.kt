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

package com.android.build.gradle.internal.scope

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/** Task Creation/Configuration Action for tasks
 *
 * This contains both meta-data to create the task ([name], [type])
 * and actions to configure the task ([preConfigure], [execute])
 */
abstract class TaskConfigAction<T : Task> : PreConfigAction<T>() {

    /** Returns the name of the task to be created.  */
    abstract val name: String

    /** Returns the class type of the task to created.  */
    abstract val type: Class<T>
}

/**
 * Configuration Actions for tasks.
 */
abstract class PreConfigAction<T: Task>: Action<T> {
    /**
     * Pre-configures the task, acting on the [TaskProvider].
     *
     * This is meant to handle configuration that must happen always, even when the task
     * is configured lazily.
     *
     * @param taskProvider the task provider
     * @param taskName the task name
     */
    open fun preConfigure(taskProvider: TaskProvider<out T>, taskName: String) {}

    /** Configures the task. */
    abstract override fun execute(task: T)
}