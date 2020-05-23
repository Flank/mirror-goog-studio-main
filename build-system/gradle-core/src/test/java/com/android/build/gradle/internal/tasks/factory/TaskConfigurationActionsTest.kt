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

package com.android.build.gradle.internal.tasks.factory

import com.google.common.truth.Truth
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.junit.Test
import org.mockito.Mockito

class TaskConfigurationActionsTest {

    enum class Stages {
        // stages for the primary actions
        MAIN_PRE_CONFIG,
        MAIN_CONFIG,
        MAIN_HANDLE_PROVIDER,
        // stages for the secondary actions
        PRE_CONFIG,
        CONFIG,
        HANDLE_PROVIDER,
    }

    private val actionRegistrar= mutableListOf<Stages>()

    private val expectedOrderedActions = listOf(
        Stages.MAIN_PRE_CONFIG,
        Stages.PRE_CONFIG,
        Stages.MAIN_HANDLE_PROVIDER,
        Stages.HANDLE_PROVIDER,
        Stages.MAIN_CONFIG,
        Stages.CONFIG)

    @Test
    fun `delayed creation`() {
        val action = createTaskAction()

        @Suppress("UNCHECKED_CAST")
        val taskProvider = Mockito.mock(TaskProvider::class.java) as TaskProvider<Task>
        Mockito.`when`(taskProvider.name).thenReturn("")
        val task = Mockito.mock(Task::class.java)
        Mockito.`when`(task.name).thenReturn("")

        // this tests the case where the registration does not trigger the configuration,
        // and therefore postRegisterHook() is called first, and then execute() on the action
        action.postRegisterHook(taskProvider)
        action.execute(task)

        // verify that all was called and in the right order
        Truth.assertThat(actionRegistrar).containsExactlyElementsIn(expectedOrderedActions).inOrder()
    }

    @Test
    fun `immediate creation`() {
        val action = createTaskAction()

        @Suppress("UNCHECKED_CAST")
        val taskProvider = Mockito.mock(TaskProvider::class.java) as TaskProvider<Task>
        Mockito.`when`(taskProvider.name).thenReturn("")
        val task = Mockito.mock(Task::class.java)
        Mockito.`when`(task.name).thenReturn("")

        // this tests the case where the registration triggers the configuration right away,
        // making postRegisterHook() called after execute()
        action.execute(task)
        action.postRegisterHook(taskProvider)

        // verify that all was called and in the right order
        Truth.assertThat(actionRegistrar).containsExactlyElementsIn(expectedOrderedActions).inOrder()
    }

    private fun createTaskAction(): TaskConfigurationActions<Task> {
        val creationAction = object: TaskCreationAction<Task>() {
            override fun configure(task: Task) {
                actionRegistrar.add(Stages.MAIN_CONFIG)
            }

            override fun preConfigure(taskName: String) {
                actionRegistrar.add(Stages.MAIN_PRE_CONFIG)
            }

            override fun handleProvider(taskProvider: TaskProvider<Task>) {
                actionRegistrar.add(Stages.MAIN_HANDLE_PROVIDER)
            }

            override val name: String
                get() = ""
            override val type: Class<Task>
                get() = Task::class.java
        }

        val preConfigAction = object: PreConfigAction {
            override fun preConfigure(taskName: String) {
                actionRegistrar.add(Stages.PRE_CONFIG)
            }
        }

        val configureAction = object: TaskConfigAction<Task> {
            override fun configure(task: Task) {
                actionRegistrar.add(Stages.CONFIG)
            }
        }

        val providerHandler = object: TaskProviderCallback<Task> {
            override fun handleProvider(taskProvider: TaskProvider<Task>) {
                actionRegistrar.add(Stages.HANDLE_PROVIDER)
            }
        }

        return TaskConfigurationActions(
            creationAction,
            preConfigAction,
            configureAction,
            providerHandler
        )
    }
}