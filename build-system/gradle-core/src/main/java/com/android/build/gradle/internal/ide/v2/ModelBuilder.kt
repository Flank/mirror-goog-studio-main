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

package com.android.build.gradle.internal.ide.v2

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.variant.VariantModel
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder

class ModelBuilder<Extension : BaseExtension>(
    private val variantModel: VariantModel
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    override fun getParameterType(): Class<ModelBuilderParameter> {
        return ModelBuilderParameter::class.java
    }

    override fun canBuild(className: String): Boolean {
        return className == AndroidProject::class.java.name
                || className == GlobalLibraryMap::class.java.name
                || className == VariantDependencies::class.java.name
                || className == ProjectSyncIssues::class.java.name
    }

    /**
     * Non-parameterized model query. Valid for all but the VariantDependencies model
     */
    override fun buildAll(className: String, project: Project): Any = when (className) {
        AndroidProject::class.java.name -> buildAndroidProjectModel(project)
        GlobalLibraryMap::class.java.name -> buildGlobalLibraryMapModel(project)
        ProjectSyncIssues::class.java.name -> buildProjectSyncIssueModel(project)
        VariantDependencies::class.java.name -> throw RuntimeException(
            "Please use parameterized Tooling API to obtain VariantDependencies model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    /**
     * Non-parameterized model query. Valid only for the VariantDependencies model
     */
    override fun buildAll(
        className: String,
        parameter: ModelBuilderParameter,
        project: Project
    ): Any = when (className) {
        VariantDependencies::class.java.name -> buildVariantDependenciesModel(project, parameter)
        AndroidProject::class.java.name,
        GlobalLibraryMap::class.java.name,
        ProjectSyncIssues::class.java.name -> throw RuntimeException(
            "Please use non-parameterized Tooling API to obtain $className model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    private fun buildAndroidProjectModel(project: Project): AndroidProject {
        throw RuntimeException("Not yet implemented")
    }

    private fun buildGlobalLibraryMapModel(project: Project): GlobalLibraryMap {
        throw RuntimeException("Not yet implemented")
    }

    private fun buildProjectSyncIssueModel(project: Project): ProjectSyncIssues {
        throw RuntimeException("Not yet implemented")
    }

    private fun buildVariantDependenciesModel(
        project: Project,
        parameter: ModelBuilderParameter
    ): VariantDependencies {
        throw RuntimeException("Not yet implemented")
    }
}