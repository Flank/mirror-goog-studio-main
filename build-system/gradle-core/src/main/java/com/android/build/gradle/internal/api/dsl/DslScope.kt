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

package com.android.build.gradle.internal.api.dsl

import com.android.build.gradle.internal.dsl.DslVariableFactory
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Scope of the DSL objects.
 *
 * This contains whatever is needed by all the DSL objects:
 * - the issue reporter
 * - the deprecation reporter
 * - the instantiator.
 * - a logger
 * - the [BuildFeatureValues]
 * - a file method (equivalent to project.file)
 */
interface DslScope {

    val issueReporter: EvalIssueReporter

    val deprecationReporter: DeprecationReporter

    val objectFactory: ObjectFactory

    val logger: Logger

    val buildFeatures: BuildFeatureValues

    val providerFactory: ProviderFactory

    val variableFactory: DslVariableFactory

    val projectLayout: ProjectLayout

    fun file(file: Any): File
}