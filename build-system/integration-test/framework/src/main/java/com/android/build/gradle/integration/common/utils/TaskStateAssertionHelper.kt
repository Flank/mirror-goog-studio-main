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

package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.integration.common.truth.TaskStateList

/** Utility to assert actual task states against expected task states. */
class TaskStateAssertionHelper(
    private val actualTaskStates: Map<String, TaskStateList.ExecutionState>
) {

    /**
     * Checks if the actual task states match the given expected task states.
     *
     * @param expectedTaskStates the expected task states
     * @param exhaustive whether the list of expected tasks is exhaustive (whether the number of
     *     expected tasks must equal the number of actual tasks)
     */
    fun assertTaskStates(
        expectedTaskStates: Map<String, TaskStateList.ExecutionState>,
        exhaustive: Boolean
    ): TaskStateAssertionHelper {
        val failedAssertions = mutableListOf<String>()
        for (task in expectedTaskStates.keys) {
            val expectedState = expectedTaskStates[task]

            if (!actualTaskStates.containsKey(task)) {
                failedAssertions.add(
                    "Task `$task` has expected state `$expectedState`" +
                            " but its actual state is not found (it was not executed)")
                continue
            }
            val actualState = actualTaskStates[task]

            if (expectedState != actualState) {
                failedAssertions.add(
                    "Task `$task` has expected state `$expectedState`" +
                            " but its actual state is `$actualState`")
            }
        }
        assert(failedAssertions.isEmpty()) { failedAssertions.joinToString("\n") }

        if (exhaustive) {
            assert(expectedTaskStates.size == actualTaskStates.size) {
                "The list of expected tasks is not exhaustive, the following tasks are missing:\n" +
                        actualTaskStates
                            .filter { it.key !in expectedTaskStates.keys}
                            .toSortedMap()
                            .map { "\"${it.key}\" to ${it.value}" }
                            .joinToString(",\n")
            }
        }

        return this
    }

    /**
     * Checks if the actual task states match the given expected task states.
     *
     * @param expectedTaskStates the expected task states
     * @param exhaustive whether the list of expected tasks is exhaustive (whether the number of
     *     expected tasks must equal the number of actual tasks)
     */
    fun assertTaskStatesByGroups(
        expectedTaskStates: Map<TaskStateList.ExecutionState, Set<String>>,
        exhaustive: Boolean
    ) {
        val taskStates = mutableMapOf<String, TaskStateList.ExecutionState>()
        for (state in expectedTaskStates.keys) {
            for (task in expectedTaskStates.getValue(state)) {
                taskStates[task] = state
            }
        }
        assertTaskStates(taskStates.toMap(), exhaustive)
    }
}