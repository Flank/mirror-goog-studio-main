/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.fixture.ModelContainerV2.ModelInfo
import com.android.builder.model.v2.AndroidModel
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject

/**
 * a Build Action that returns all the [AndroidProject]s and all [ProjectSyncIssues] for all the
 * sub-projects, via the tooling API.
 *
 * This is returned as a [ModelContainer]
 */
class GetAndroidModelV2Action<ModelT : AndroidModel, ParamT> private constructor(
    private val modelClass: Class<ModelT>,
    private val paramClassAndModifier: Pair<Class<ParamT>, ((ParamT) -> Unit)>?
) : BuildAction<ModelContainerV2<ModelT>> {

    constructor(modelClass: Class<ModelT>) : this(modelClass, null)
    constructor(
        modelClass: Class<ModelT>,
        paramClass: Class<ParamT>,
        paramModifier: (ParamT) -> Unit
    ) : this(modelClass, paramClass to paramModifier)

    override fun execute(buildController: BuildController): ModelContainerV2<ModelT> {
        val t1 = System.currentTimeMillis()

        // accumulate pairs of (build Id, project) to query.
        val projects = mutableListOf<Pair<BuildIdentifier, BasicGradleProject>>()

        val rootBuild = buildController.buildModel
        val rootBuildId = rootBuild.buildIdentifier

        // add the projects of the root build.
        for (project in rootBuild.projects) {
            projects.add(rootBuildId to project)
        }

        // and the included builds
        for (build in rootBuild.includedBuilds) {
            val buildId = build.buildIdentifier
            for (project in build.projects) {
                projects.add(buildId to project)
            }
        }

        val modelMap = getAndroidProjectMap(projects, buildController)

        // if the queried model was the dependencies, then get the library map
        val libraryMap = if (modelClass == VariantDependencies::class.java) {
            projects.firstOrNull()?.let { (_, project) ->
                buildController.findModel(project, GlobalLibraryMap::class.java)
            }
        } else {
            null
        }

        val t2 = System.currentTimeMillis()

        println("GetAndroidModelV2Action: " + (t2 - t1) + "ms")

        return ModelContainerV2(
            rootBuildId,
            modelMap,
            libraryMap
        )
    }

    private fun getAndroidProjectMap(
        projects: List<Pair<BuildIdentifier, BasicGradleProject>>,
        buildController: BuildController
    ): Map<BuildIdentifier, MutableMap<String, ModelInfo<ModelT>>> {
        val models = mutableMapOf<BuildIdentifier, MutableMap<String, ModelInfo<ModelT>>>()

        for ((buildId, project) in projects) {
            val model = if (paramClassAndModifier == null) {
                buildController.findModel(project, modelClass)
            } else {
                buildController.findModel(
                    project,
                    modelClass,
                    paramClassAndModifier.first,
                    paramClassAndModifier.second
                )
            } ?: continue

            val issues =
                buildController.findModel(project, ProjectSyncIssues::class.java) ?: continue

            val map = models.computeIfAbsent(buildId) { mutableMapOf() }
            map[project.path] = ModelInfo(model, issues)
        }

        return models
    }
}