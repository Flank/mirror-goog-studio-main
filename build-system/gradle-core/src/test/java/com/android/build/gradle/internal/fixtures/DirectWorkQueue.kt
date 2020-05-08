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

package com.android.build.gradle.internal.fixtures

import com.android.build.gradle.internal.fixtures.ProjectFactory.Companion.project
import org.gradle.api.Action
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** An implementation of [WorkQueue] that executes [WorkAction]s directly */
class DirectWorkQueue(private val workParametersClass: Class<out WorkParameters>) : WorkQueue {

    override fun <T : WorkParameters?> submit(
        workActionClass: Class<out WorkAction<T>>?,
        parameterAction: Action<in T>?
    ) {
        val workParameters = project.objects.newInstance(workParametersClass)
        @Suppress("UNCHECKED_CAST")
        parameterAction?.execute(workParameters as T)
        workActionClass?.let { project.objects.newInstance(it, workParameters).execute() }
    }

    override fun await() {
        // do nothing.
    }
}