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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * Service object for the project, containing a bunch of project-provided items that can be exposed
 * to different stages of the plugin work.
 *
 * This is not meant to be exposed directly though. It's meant to be a convenient storage for
 * all these objects so that they don't have to be recreated or passed to methods/constructors
 * all the time.
 *
 * Stage-specific services should expose only part of what these objects expose, based on the need
 * of the context.
 */
class ProjectServices(
    val issueReporter: IssueReporter,
    val deprecationReporter: DeprecationReporter,
    val objectFactory: ObjectFactory,
    val logger: Logger,
    val providerFactory: ProviderFactory,
    val projectLayout: ProjectLayout,
    val projectOptions: ProjectOptions,
    val fileResolver: (Any) -> File
)