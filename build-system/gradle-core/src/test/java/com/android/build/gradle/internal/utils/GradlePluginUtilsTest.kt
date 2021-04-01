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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.fixtures.FakeResolutionResult
import com.android.build.gradle.internal.fixtures.addDependencyEdge
import com.android.build.gradle.internal.fixtures.createModuleComponent
import com.android.ide.common.repository.GradleVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test for GradlePluginUtils methods.
 */
class GradlePluginUtilsTest {

    @Test
    fun testViolatingDependency() {
        val violatingDependency = createModuleComponent(
            group = "org.jetbrains.kotlin",
            name = "kotlin-gradle-plugin",
            version = "1.0"
        )
        val rootDependency = createModuleComponent("", "project", "unspecified")
        val projectDisplayName = "root project 'project'"
        addDependencyEdge(rootDependency, violatingDependency)
        val buildscriptClasspath = FakeResolutionResult(rootDependency)

        val dependencyInfo = DependencyInfo(
            displayName = "Kotlin",
            dependencyGroup = "org.jetbrains.kotlin",
            dependencyName = "kotlin-gradle-plugin",
            minimumVersion = GradleVersion.parse("1.3.10")
        )

        val pathsToViolatingPlugins = ViolatingPluginDetector(buildscriptClasspath, dependencyInfo, projectDisplayName).detect()
        assertThat(pathsToViolatingPlugins)
            .contains("root project 'project' -> org.jetbrains.kotlin:kotlin-gradle-plugin:1.0")
    }

    @Test
    fun testNonViolatingDependency() {
        val nonViolatingDependency = createModuleComponent(
            group = "org.jetbrains.kotlin",
            name = "kotlin-gradle-plugin",
            version = "1.3.10"
        )
        val rootDependency = createModuleComponent("", "project", "unspecified")
        val projectDisplayName = "root project 'project'"
        addDependencyEdge(rootDependency, nonViolatingDependency)
        val buildscriptClasspath = FakeResolutionResult(rootDependency)

        val dependencyInfo = DependencyInfo(
            displayName = "Kotlin",
            dependencyGroup = "org.jetbrains.kotlin",
            dependencyName = "kotlin-gradle-plugin",
            minimumVersion = GradleVersion.parse("1.3.10")
        )

        val pathsToViolatingPlugins = ViolatingPluginDetector(buildscriptClasspath, dependencyInfo, projectDisplayName).detect()
        assertThat(pathsToViolatingPlugins).isEmpty()
    }
}
