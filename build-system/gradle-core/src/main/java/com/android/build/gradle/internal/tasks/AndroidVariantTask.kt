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

package com.android.build.gradle.internal.tasks

import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import java.lang.RuntimeException

/** Base Android task with a variant name for analytics.  */
abstract class AndroidVariantTask : DefaultTask(), VariantAwareTask {

    @Internal("No influence on output, this is for our build stats reporting mechanism")
    override lateinit var variantName: String

    /**
     * Called by subclasses that want to record the non incremental task execution in the profiling
     * information.
     *
     * The task execution will use [GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES]
     * as the span type to record the [AndroidVariantTask.recordedTaskAction].
     */
    protected fun <E : Exception> recordTaskAction() {
        Blocks.recordSpan<Unit, E>(
            project.name,
            path,
            GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES
        ) { recordedTaskAction() }
    }

    /**
     * Recorded task action to be overridden with the non incremental task implementation.
     */
    @Throws(Exception::class)
    protected open fun recordedTaskAction() {
        throw RuntimeException("recordedTaskAction on ${javaClass.name} must be overridden")
    }

    /**
     * Called by subclasses that want to record the task execution in the profiling
     * information.
     *
     * The task execution will use [GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES]
     * as the span type to record the [AndroidVariantTask.recordedTaskAction].
     */
    protected fun <E: Exception> recordTaskAction(inputs: IncrementalTaskInputs) {
        Blocks.recordSpan<Unit, E>(
            project.name,
            path,
            GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES
        ) { recordedTaskAction(inputs) }
    }

    /**
     * Recorded task action to be overridden with incremental task implementation.
     */
    @Throws(Exception::class)
    protected open fun recordedTaskAction(inputs: IncrementalTaskInputs) {
        throw RuntimeException("recordedTaskAction on ${javaClass.name} must be overridden")
    }
}
