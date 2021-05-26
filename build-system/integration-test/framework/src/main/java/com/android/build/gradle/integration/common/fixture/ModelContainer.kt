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

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.GlobalLibraryMap
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.ndk.NativeModule
import org.gradle.tooling.model.BuildIdentifier
import java.io.Serializable

/**
 * The object returned by the model action via the tooling API.
 *
 * This is meant to contain both the model and the associated sync issue.
 */
class ModelContainerV2(
    val rootBuildId: BuildIdentifier,
    val infoMaps: Map<BuildIdentifier, Map<String, ModelInfo>>,
    val globalLibraryMap: GlobalLibraryMap? = null
) : Serializable {

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }

    data class ModelInfo(
        val versions: Versions,
        val androidProject: AndroidProject?,
        val androidDsl: AndroidDsl?,
        val variantDependencies: VariantDependencies?,
        val nativeModule: NativeModule?,
        val issues: ProjectSyncIssues
    ): Serializable {
        companion object {
            @JvmStatic
            private val serialVersionUID: Long = 1L
        }
    }

    /**
     * Returns the only [Versions] when there is no composite builds and a single sub-project.
     */
    val singleVersions: Versions
        get() = singleInfo.versions

    /**
     * Returns the only [AndroidProject] when there is no composite builds and a single sub-project.
     */
    val singleAndroidProject: AndroidProject
        get() = singleInfo.androidProject
                ?: throw RuntimeException("No AndroidProject model for project '${singleInfoPath}'")

    /**
     * Returns the only [AndroidDsl] when there is no composite builds and a single sub-project.
     */
    val singleAndroidDsl: AndroidDsl
        get() = singleInfo.androidDsl
                ?: throw RuntimeException("No AndroidDsl model for project '${singleInfoPath}'")

    /**
     * Returns the only [VariantDependencies] when there is no composite builds and a single sub-project.
     */
    val singleVariantDependencies: VariantDependencies
        get() = singleInfo.variantDependencies
                ?: throw RuntimeException("No AndroidProject model for project '${singleInfoPath}'")

    /**
     * Returns the only [NativeModule] when there is no composite builds and a single sub-project
     * setup for native builds
     *
     * (there could be more than one Android sub-project, as long as only one sets up the native
     * build)
     */
    val singleNativeModule: NativeModule
        get() = infoMaps.values.flatMap { it.values }.mapNotNull { it.nativeModule }.single()

    /**
     * Returns the only [ProjectSyncIssues] model when there is no composite builds and a single
     * Android sub-project.
     */
    val singleProjectIssues: ProjectSyncIssues
        get() = singleInfo.issues

    /**
     * Returns the single [ModelInfo] when there is no composite builds and a single
     * Android sub-project.
     */
    val singleInfo: ModelInfo
        get() {
            if (infoMaps.size != 1) {
                throw RuntimeException("Found ${infoMaps.size} builds when querying for single: ${infoMaps.keys}")
            }
            return rootInfoMap.values.single()
        }

    /** Returns the only model map. This is only valid if there is no included builds.  */
    private val singleInfoPath: String
        get() {
            if (infoMaps.size != 1) {
                throw RuntimeException("Found ${infoMaps.size} builds when querying for single: ${infoMaps.keys}")
            }
            return rootInfoMap.keys.single()
        }


    /** Returns the only model map. This is only valid if there is no included builds.  */
    private val singleInfoMap: Map<String, ModelInfo>
        get() {
            if (infoMaps.size != 1) {
                throw RuntimeException("Found ${infoMaps.size} builds when querying for single: ${infoMaps.keys}")
            }
            return rootInfoMap
        }

    /**
     * returns the project map for the root build
     */
    val rootInfoMap: Map<String, ModelInfo>
        get() {
            return infoMaps[rootBuildId] ?: throw RuntimeException("failed to find project map for root build id: $rootBuildId\nMap = $infoMaps")
        }
}
