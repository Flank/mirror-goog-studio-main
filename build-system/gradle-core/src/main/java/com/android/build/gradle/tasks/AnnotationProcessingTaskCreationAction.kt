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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.LazyTaskCreationAction
import org.gradle.api.Task

/**
 * Convenient super class for CreationAction implementation that will process all annotated
 * input and output properties. Each input and output will be looked up in the scope and
 * pre-allocated during the [LazyTaskCreationAction.preConfigure] call.
 *
 * Once the task is created and the [LazyTaskCreationAction.configure] is invoked, the pre-allocated
 * are transferred to the relevant input and output fields of the task instance.
 */
open class AnnotationProcessingTaskCreationAction<T: Task>(
    protected val scope: VariantScope,
    override val name: String,
    override val type: Class<T>): LazyTaskCreationAction<T>() {

    private val artifactsHolder= TaskArtifactsHolder<T>(scope.artifacts)

    override fun preConfigure(taskName: String) {
        artifactsHolder.allocateArtifacts(this)
    }

    override fun configure(task: T)  {
        artifactsHolder.transfer(task)
    }
}