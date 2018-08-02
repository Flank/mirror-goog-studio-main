/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

@Suppress("OverridingDeprecatedMember", "DEPRECATION")
class TaskFactoryImpl(private val taskContainer: TaskContainer):
    TaskFactory {

    override fun containsKey(name: String): Boolean {
        return taskContainer.findByName(name) != null
    }

    // --- Direct Creation ---

    override fun <T : Task> eagerCreate(creationAction: EagerTaskCreationAction<T>): T {
        return taskContainer.create(creationAction.name, creationAction.type, creationAction)
    }

    override fun <T : Task> eagerCreate(
        taskName: String, taskClass: Class<T>, action: Action<in T>): T {
        return taskContainer.create(taskName, taskClass, action)
    }

    override fun eagerCreate(taskName: String, action: Action<in Task>): Task {
        return eagerCreate(taskName, Task::class.java, action)
    }

    override fun findByName(name: String): Task? {
        return taskContainer.findByName(name)
    }

    override fun eagerConfigure(name: String, configAction: Action<in Task>) {
        val task = taskContainer.getByName(name)
        configAction.execute(task)
    }

    // --- Lazy Creation ---

    override fun lazyCreate(name: String): TaskProvider<Task> = taskContainer.register(name)

    override fun <T : Task> lazyCreate(creationAction: LazyTaskCreationAction<T>): TaskProvider<T> =
        taskContainer.lazyCreate(creationAction, null, null)

    override fun <T : Task> lazyCreate(
        creationAction: LazyTaskCreationAction<T>,
        secondaryPreConfigAction: PreConfigAction?,
        secondaryAction: TaskConfigAction<in T>?
    ): TaskProvider<T> =
        taskContainer.lazyCreate(creationAction, secondaryPreConfigAction, secondaryAction)

    override fun lazyCreate(
        taskName: String,
        preConfigAction: PreConfigAction?,
        action: TaskConfigAction<in Task>?,
        providerCallback: TaskProviderCallback<Task>?
    ): TaskProvider<Task> =
        taskContainer.lazyCreate(taskName, Task::class.java, preConfigAction, action, providerCallback)

    override fun lazyConfigure(name: String, action: Action<in Task>) {
        taskContainer.named(name).configure(action)
    }

    override fun <T : Task> lazyConfigure(name: String, type: Class<T>, action: Action<in T>) {
        taskContainer.withType(type).named(name).configure(action)
    }
}