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
import org.gradle.api.DefaultTask
import org.gradle.api.Task

/**
 * Interface for a container that can create Task.
 */
interface TaskFactory {
    /** Returns true if this collection contains an item with the given name.  */
    fun containsKey(name: String): Boolean

    /**
     * Returns the [Task] named name from the current set of defined tasks.
     *
     * @param name the name of the requested [Task]
     * @return the [Task] instance or null if not found.
     */
    fun findByName(name: String): Task?

    /** Creates a task with the given name.  */
    fun eagerCreate(name: String): Task

    /** Creates a task with the given name and type.  */
    fun <S : Task> eagerCreate(name: String, type: Class<S>): S

    /** Creates a task with the given [EagerTaskCreationAction]  */
    fun <T : Task> eagerCreate(creationAction: EagerTaskCreationAction<T>): T

    /** Creates a task wit the given name, type and configuration action  */
    fun <T : Task> eagerCreate(
        taskName: String, taskClass: Class<T>, configAction: Action<T>
    ): T

    /** Creates a task with the given name and config action.  */
    fun eagerCreate(taskName: String, configAction: Action<DefaultTask>): DefaultTask

    /** Applies the given configAction to the task with given name.  */
    fun eagerConfigure(name: String, configAction: Action<in Task>)
}
