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

package com.android.build.gradle.internal.variant2

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dsl.DslVariableFactory
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.ProjectScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

class DslScopeImpl(
        private val projectScope: ProjectScope,
        override val buildFeatures: BuildFeatureValues,
        override val variableFactory: DslVariableFactory
) : DslScope {

        override val issueReporter: IssueReporter
                get() = projectScope.issueReporter
        override val deprecationReporter: DeprecationReporter
                get() = projectScope.deprecationReporter
        override val objectFactory: ObjectFactory
                get() = projectScope.objectFactory
        override val logger: Logger
                get() = projectScope.logger
        override val providerFactory: ProviderFactory
                get() = projectScope.providerFactory
        override val projectLayout: ProjectLayout
                get() = projectScope.projectLayout
        override val projectOptions: ProjectOptions
                get() = projectScope.projectOptions
        override fun file(file: Any): File = projectScope.fileResolver(file)
}