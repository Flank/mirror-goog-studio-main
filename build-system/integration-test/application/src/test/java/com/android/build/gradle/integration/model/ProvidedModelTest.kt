/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.getAndroidTestArtifact
import com.android.builder.model.AndroidProject
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/**
 * This compares the list of provided libraries returned by v1 and v2.
 */
class ProvidedModelTest {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                api("com.android.support:appcompat-v7:+")
                api("com.google.guava:guava:19.0")
                api("com.android.support.constraint:constraint-layout:1.0.2")
                testImplementation("junit:junit:4.12")
                androidTestImplementation("com.android.support.test:runner:+")
                androidTestImplementation("com.android.support.test.espresso:espresso-core:+")
            }
        }
    }

    companion object {
        private val providedAndroidLibraries = listOf(
            "com.android.support:appcompat-v7:28.0.0",
            "com.android.support.constraint:constraint-layout:1.0.2",
            "com.android.support:support-fragment:28.0.0",
            "com.android.support:animated-vector-drawable:28.0.0",
            "com.android.support:support-core-ui:28.0.0",
            "com.android.support:support-core-utils:28.0.0",
            "com.android.support:support-vector-drawable:28.0.0",
            "com.android.support:loader:28.0.0",
            "com.android.support:viewpager:28.0.0",
            "com.android.support:coordinatorlayout:28.0.0",
            "com.android.support:drawerlayout:28.0.0",
            "com.android.support:slidingpanelayout:28.0.0",
            "com.android.support:customview:28.0.0",
            "com.android.support:swiperefreshlayout:28.0.0",
            "com.android.support:asynclayoutinflater:28.0.0",
            "com.android.support:support-compat:28.0.0",
            "com.android.support:versionedparcelable:28.0.0",
            "com.android.support:cursoradapter:28.0.0",
            "android.arch.lifecycle:runtime:1.1.1",
            "com.android.support:documentfile:28.0.0",
            "com.android.support:localbroadcastmanager:28.0.0",
            "com.android.support:print:28.0.0",
            "android.arch.lifecycle:viewmodel:1.1.1",
            "android.arch.lifecycle:livedata:1.1.1",
            "android.arch.lifecycle:livedata-core:1.1.1",
            "android.arch.core:runtime:1.1.1",
            "com.android.support:interpolator:28.0.0"
        )

        private val providedJavaLibraries = listOf(
            "android.arch.lifecycle:common:1.1.1",
            "android.arch.core:common:1.1.1",
            "com.android.support:collections:28.0.0",
            "com.android.support.constraint:constraint-layout-solver:1.0.2",
            "com.google.guava:guava:19.0"
        )
    }

    @Test
    fun `test v1 isProvided`() {
        val result = project
            .model()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .level(AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD)
            .fetchAndroidProjects()

        val model = result.modelMaps[result.rootBuildId]?.get(":app")
            ?: throw RuntimeException("Cannot find model for :app")

        val debug = model.variants.single { it.name == "debug" }
        val dependencies = debug.mainArtifact.dependencies

        Truth.assertThat(dependencies.libraries).isNotEmpty()
        Truth.assertThat(dependencies.libraries.filter { it.isProvided }).isEmpty()

        val androidTestDeps = debug.getAndroidTestArtifact().dependencies
        Truth.assertThat(androidTestDeps.libraries).isNotEmpty()

        // Test the Android Libraries
        val providedAndroidLibs = androidTestDeps.libraries.filter { it.isProvided }.map {
            val coord = it.resolvedCoordinates
            "${coord.groupId}:${coord.artifactId}:${coord.version}"
        }
        Truth.assertThat(providedAndroidLibs).containsExactlyElementsIn(providedAndroidLibraries)

        // Test the Java Libraries
        val providedJavaLibs = androidTestDeps.javaLibraries.filter { it.isProvided }.map {
            val coord = it.resolvedCoordinates
            "${coord.groupId}:${coord.artifactId}:${coord.version}"
        }
        Truth.assertThat(providedJavaLibs).containsExactlyElementsIn(providedJavaLibraries)
    }

    @Test
    fun `test v2 isProvided`() {
        val result = project
            .modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val dependencies = result.container.rootInfoMap[":app"]?.variantDependencies?.androidTestArtifact
            ?: throw RuntimeException("Cannot find model for :app")

        // gather the compile and runtime Graphitem (ie flatten the graph)
        val compileItems: Set<GraphItem> = dependencies.compileDependencies.flatten()
        val runtimeItems: Set<GraphItem> = dependencies.runtimeDependencies.flatten()

        // convert to keys, using the library map
        val map = result.container.globalLibraryMap?.libraries
            ?: throw RuntimeException("No library map!")

        // Test the Android Libraries
        checkLibraries(compileItems, runtimeItems, LibraryType.ANDROID_LIBRARY, map, providedAndroidLibraries)

        // Test the Java Libraries
        checkLibraries(compileItems, runtimeItems, LibraryType.JAVA_LIBRARY, map, providedJavaLibraries)
    }

    private fun checkLibraries(
        compileItems: Set<GraphItem>,
        runtimeItems: Set<GraphItem>,
        libraryType: LibraryType,
        libraryMap: Map<String, Library>,
        actualProvidedList: List<String>
    ) {
        val compileIdentityItems = compileItems.mapNotNull { it.convert(libraryMap, libraryType) }
        val runtimeIdentityItems = runtimeItems.mapNotNull { it.convert(libraryMap, libraryType) }

        // get provided list
        val providedList = compileIdentityItems.minus(runtimeIdentityItems)
        Truth.assertThat(providedList.map { it.coordinates }).containsExactlyElementsIn(actualProvidedList)
    }

    private fun List<GraphItem>?.flatten(): Set<GraphItem> {
        return this?.let { list ->
            (list.flatMap { it.dependencies.flatten() } + list).toSet()
        } ?: setOf()
    }

    private fun GraphItem.convert(libraryMap: Map<String, Library>, type: LibraryType): Identity? {
        val library = libraryMap[key] ?: return null

        if (library.type != type) {
            return null
        }

        val info = library.libraryInfo ?: throw RuntimeException("No library info")

        return Identity(
            "${info.group}:${info.name}:${info.version}",
            info.capabilities
        )
    }

    private data class Identity(
        val coordinates: String,
        val capabilities: List<String>
    )
}
