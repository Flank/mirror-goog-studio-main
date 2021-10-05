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

import com.android.build.gradle.integration.common.fixture.ModelContainerV2.BuildInfo
import com.android.build.gradle.integration.common.fixture.ModelContainerV2.ModelInfo
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.BuildMap
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModelBuilderParameter
import com.android.builder.model.v2.models.ndk.NativeModule
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import java.io.File

/**
 * a Build Action that returns all the [AndroidProject]s and all [ProjectSyncIssues] for all the
 * sub-projects, via the tooling API.
 *
 * This is returned as a [ModelContainer]
 */
class GetAndroidModelV2Action(
    private val variantName: String? = null,
    private val nativeParams: ModelBuilderV2.NativeModuleParams? = null
) : BuildAction<ModelContainerV2> {

    override fun execute(buildController: BuildController): ModelContainerV2 {
        val t1 = System.currentTimeMillis()

        // accumulate pairs of (build Id, project) to query.
        val projects = mutableListOf<Pair<BuildIdentifier, BasicGradleProject>>()
        val projectMap = mutableMapOf<BuildIdentifier, List<Pair<String, File>>>()

        val rootBuild = buildController.buildModel
        val rootBuildId = rootBuild.buildIdentifier

        // add the projects of the root build.
        val projectList = rootBuild.projects
        for (project in projectList) {
            projects.add(rootBuildId to project)
        }
        projectMap[rootBuildId] = rootBuild.projects.map { it.path to it.projectDirectory }

        // and the included builds
        for (build in rootBuild.includedBuilds) {
            val buildId = build.buildIdentifier
            for (project in build.projects) {
                projects.add(buildId to project)
            }
            projectMap[buildId] = build.projects.map { it.path to it.projectDirectory }
        }

        val (modelMap, buildMap) = getAndroidProjectMap(projects, buildController)

        val t2 = System.currentTimeMillis()

        println("GetAndroidModelV2Action: " + (t2 - t1) + "ms")

        val buildIdMap = buildMap?.buildIdMap ?: mapOf(":" to rootBuildId.rootDir)

        val reverseBuildIdMap = buildIdMap.map { it.value to it.key}.toMap()

        // build the final infoMaps and the final buildMap
        val projectInfoMaps = mutableMapOf<String, Map<String, ModelInfo>>()
        val buildInfoMap = mutableMapOf<String, BuildInfo>()
        for ((buildId, modelInfoMap) in modelMap) {
            val name = reverseBuildIdMap[buildId.rootDir]
                ?: throw RuntimeException("Failed to find name for ${buildId.rootDir}\nbuildIdMap = $buildIdMap")
            projectInfoMaps[name] = modelInfoMap.filter {
                it.value.isAndroid()
            }

            buildInfoMap[name] = BuildInfo(
                name,
                buildId.rootDir,
                modelInfoMap.map { it.key to it.value.projectDir }
            )
        }

        return ModelContainerV2(projectInfoMaps, buildInfoMap)
    }

    private data class Result(
        val modelMap: Map<BuildIdentifier, Map<String, ModelInfo>>,
        val buildMap: BuildMap?,
    )

    private fun getAndroidProjectMap(
        projects: List<Pair<BuildIdentifier, BasicGradleProject>>,
        buildController: BuildController
    ): Result {
        val models = mutableMapOf<BuildIdentifier, MutableMap<String, ModelInfo>>()

        var buildMap: BuildMap? = null

        for ((buildId, project) in projects) {
            // if we don't find ModelVersions, then it's not an AndroidProject, move on.
            val modelVersions = buildController.findModel(project, Versions::class.java)
            if (modelVersions != null) {
                if (buildMap == null) {
                    buildMap = buildController.findModel(project, BuildMap::class.java)
                }
                val basicAndroidProject = buildController.findModel(project, BasicAndroidProject::class.java)
                val androidProject = buildController.findModel(project, AndroidProject::class.java)
                val androidDsl = buildController.findModel(project, AndroidDsl::class.java)

                val variantDependencies = if (variantName != null) {
                    buildController.findModel(
                        project,
                        VariantDependencies::class.java,
                        ModelBuilderParameter::class.java
                    ) { it.variantName = variantName }
                } else null

                val nativeModule = if (nativeParams != null) {
                    buildController.findModel(
                        project,
                        NativeModule::class.java,
                        NativeModelBuilderParameter::class.java
                    ) {
                        it.variantsToGenerateBuildInformation = nativeParams.nativeVariants
                        it.abisToGenerateBuildInformation = nativeParams.nativeAbis
                    }
                } else null

                val issues =
                    buildController.findModel(project, ProjectSyncIssues::class.java)
                        ?: throw RuntimeException("No ProjectSyncIssue for ${project.path}")

                val map = models.computeIfAbsent(buildId) { mutableMapOf() }
                map[project.path] =
                    ModelInfo(
                        project.projectDirectory,
                        modelVersions,
                        basicAndroidProject,
                        androidProject,
                        androidDsl,
                        variantDependencies,
                        nativeModule,
                        issues
                    )
            } else {
                val map = models.computeIfAbsent(buildId) { mutableMapOf() }
                map[project.path] =
                    ModelInfo(
                        project.projectDirectory,
                        versions = null,
                        basicAndroidProject = null,
                        androidProject = null,
                        androidDsl = null,
                        variantDependencies = null,
                        nativeModule = null,
                        issues = null
                    )
            }
        }

        return Result(models, buildMap)
    }
}
