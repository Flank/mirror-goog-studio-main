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

import com.android.build.gradle.integration.common.fixture.AndroidProjectContainer.ProjectInfo
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.ProjectSyncIssues
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject

/**
 * a Build Action that returns all the [AndroidProject]s and all [ProjectSyncIssues] for all the
 * sub-projects, via the tooling API.
 *
 * This is returned as a [AndroidProjectContainer]
 */
class GetAndroidProjectV2Action : BuildAction<AndroidProjectContainer> {

    override fun execute(buildController: BuildController): AndroidProjectContainer {
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

        val t2 = System.currentTimeMillis()

        println("GetAndroidModelAction: " + (t2 - t1) + "ms")

        return AndroidProjectContainer(
            rootBuildId,
            modelMap)
    }

    private fun getAndroidProjectMap(
        projects: List<Pair<BuildIdentifier, BasicGradleProject>>,
        buildController: BuildController
    ): Map<BuildIdentifier, MutableMap<String, ProjectInfo>> {
        val models = mutableMapOf<BuildIdentifier, MutableMap<String, ProjectInfo>>()

        for ((buildId, project) in projects) {
            val androidProject = buildController.findModel(project, AndroidProject::class.java) ?: continue
            val issues = buildController.findModel(project, ProjectSyncIssues::class.java) ?: continue

            val map = models.computeIfAbsent(buildId) { mutableMapOf() }
            map[project.path] = ProjectInfo(androidProject, issues)
        }

        return models
    }
}