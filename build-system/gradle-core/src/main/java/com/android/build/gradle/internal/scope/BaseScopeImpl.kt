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

package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter

/**
 * Impl for BaseScope over a [ProjectScope]
 */
abstract class BaseScopeImpl(protected val projectScope: ProjectScope): BaseScope {

    override fun <T> newInstance(type: Class<T>, vararg args: Any?): T = projectScope.objectFactory.newInstance(type, *args)

    override val issueReporter: IssueReporter
        get() = projectScope.issueReporter
    override val deprecationReporter: DeprecationReporter
        get() = projectScope.deprecationReporter
    override val projectOptions: ProjectOptions
        get() = projectScope.projectOptions
}