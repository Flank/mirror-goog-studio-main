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

package com.android.build.gradle.internal.workeractions

import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import javax.inject.Inject

abstract class WorkActionAdapter<T: WorkParameters, U: WorkActionAdapter.Parameters<T>>
    @Inject constructor(val objectFactory: ObjectFactory)
    : WorkAction<U> {

    open class Parameters<T: WorkParameters>: WorkParameters {
        lateinit var adaptedParameters: T
        lateinit var adaptedAction: Class<out AgpWorkAction<T>>
    }

    override fun execute() {
        val workAction = objectFactory.newInstance(parameters.adaptedAction)
        workAction.params = parameters.adaptedParameters
        workAction.execute()
    }
}