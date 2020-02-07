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

import com.android.build.gradle.internal.dsl.DslVariableFactory
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

class DslServicesImpl(
        projectServices: ProjectServices,
        override val variableFactory: DslVariableFactory
): BaseServicesImpl(projectServices),
        DslServices {

        override val issueReporter: IssueReporter
                get() = projectServices.issueReporter
        override val deprecationReporter: DeprecationReporter
                get() = projectServices.deprecationReporter
        override val objectFactory: ObjectFactory
                get() = projectServices.objectFactory
        override val logger: Logger
                get() = projectServices.logger
        override val providerFactory: ProviderFactory
                get() = projectServices.providerFactory
        override val projectLayout: ProjectLayout
                get() = projectServices.projectLayout
        override val projectOptions: ProjectOptions
                get() = projectServices.projectOptions
        override fun file(file: Any): File = projectServices.fileResolver(file)
}