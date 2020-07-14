/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.ProjectSyncIssues
import org.gradle.tooling.model.BuildIdentifier
import java.io.Serializable

/**
 * The object returned by the model action via the tooling API.
 *
 * This is used to query for AndroidProject v2 (with no dependencies)
 */
class AndroidProjectContainer(
    val rootBuildId: BuildIdentifier,
    val infoMaps: Map<BuildIdentifier, Map<String, ProjectInfo>>
) : Serializable {

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }

    data class ProjectInfo(
        val project: AndroidProject,
        val issues: ProjectSyncIssues
    ): Serializable {
        companion object {
            @JvmStatic
            private val serialVersionUID: Long = 1L
        }
    }

    /**
     * Returns the only model when there is no composite builds and a single sub-project.
     */
    val singleAndroidProject: AndroidProject
        get() = singleInfo.project

    /**
     * Returns the only SyncIssue model when there is no composite builds and a single sub-project.
     */
    val singleProjectIssues: ProjectSyncIssues
        get() = singleInfo.issues

    /**
     * Retursn the single ProjectInfo (containing both AndroidProject and ProjecSyncIssues)
     * when there is no composite builds and a single sub-project.
     */
    val singleInfo: ProjectInfo
        get() {
            if (infoMaps.size != 1) {
                throw RuntimeException("Found ${infoMaps.size} builds when querying for single: ${infoMaps.keys}")
            }
            return rootInfoMap.values.single()
        }

    /** Returns the only model map. This is only valid if there is no included builds.  */
    val singleInfoMap: Map<String, ProjectInfo>
        get() {
            if (infoMaps.size != 1) {
                throw RuntimeException("Found ${infoMaps.size} builds when querying for single: ${infoMaps.keys}")
            }
            return rootInfoMap
        }

    /**
     * returns the project map for the root build
     */
    val rootInfoMap: Map<String, ProjectInfo>
        get() = infoMaps[rootBuildId] ?: throw RuntimeException("failed to find project map for root build id")

}