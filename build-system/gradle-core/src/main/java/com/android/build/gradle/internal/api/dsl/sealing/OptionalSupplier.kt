/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.dsl.sealing

import com.android.build.api.dsl.Initializable
import com.android.builder.errors.EvalIssueReporter

/**
 * An optional supplier of a instance provided by a lamdba.
 */
class OptionalSupplier<T: InitializableSealable<T>>(private val instantiator: () -> T) {

    private var localInstance: T? = null

    fun get(sealIt: Boolean): T {
        if (localInstance == null) {
            localInstance = instantiator.invoke()
            if (sealIt) {
                localInstance?.seal()
            }
        }

        return localInstance!!
    }

    fun copyFrom(from: OptionalSupplier<T>) {
        val value = from.instance

        if (value != null) {
            get(false).initWith(value)
        } else {
            localInstance = null
        }
    }

    val instance: T?
        get() = localInstance

    fun hasInstance(): Boolean = localInstance != null
}

abstract class InitializableSealable<in T: Initializable<T>>(issueReporter: EvalIssueReporter):
        SealableObject(issueReporter), Initializable<T>