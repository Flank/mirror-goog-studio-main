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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class AutoNamespaceTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("namespacedApp")
        .create()

    @Test
    fun rewriteJavaBytecodeAndRClasses() {
        TestFileUtils.searchAndReplace(
                FileUtils.join(project.mainSrcDir, "com", "example", "namespacedApp", "Test.java"),
                "int test;",
                """
        // Check java references.
        String javaRef = android.support.constraint.BuildConfig.BUILD_TYPE;
        // Check namespaced resource references.
        int resRef = android.support.constraint.R.attr.layout_constraintBaseline_creator;"""
        )

        TestFileUtils.appendToFile(
                project.buildFile,
                """dependencies {
    implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
    implementation 'com.android.support.constraint:constraint-layout:1.0.2'
}"""
        )

        project.executor()
            .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
            .run("assembleDebug")

        assertThat(
                FileUtils.join(
                        project.intermediatesDir,
                        "namespaced_classes_jar",
                        "debug",
                        "autoNamespaceDebugDependencies",
                        "namespaced-classes.jar"))
            .exists()

        assertThat(
                FileUtils.join(
                        project.intermediatesDir,
                        "compile_only_namespaced_dependencies_r_jar",
                        "debug",
                        "autoNamespaceDebugDependencies",
                        "namespaced-R.jar"))
            .exists()

        TestFileUtils.searchAndReplace(
                FileUtils.join(project.mainSrcDir, "com", "example", "namespacedApp", "Test.java"),
                "layout_constraintBaseline_creator",
                "layout_constraintBaseline_toBaselineOf"
        )

        val result = project.executor()
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .run("assembleDebug")

        Truth.assertThat(result.upToDateTasks).contains(":autoNamespaceDebugDependencies")
        Truth.assertThat(result.notUpToDateTasks).doesNotContain(":autoNamespaceDebugDependencies")
    }

    @Test
    fun incorrectReferencesAreStillInvalid() {
        TestFileUtils.searchAndReplace(
                FileUtils.join(project.mainSrcDir, "com", "example", "namespacedApp", "Test.java"),
                "int test;",
                """
        // Check namespaced resource references.
        int resRef = android.support.constraint.R.attr.invalid_reference;"""
        )

        TestFileUtils.appendToFile(
                project.buildFile,
                """dependencies {
    implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
    implementation 'com.android.support.constraint:constraint-layout:$TEST_CONSTRAINT_LAYOUT_VERSION'
}"""
        )

        val result = project.executor()
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .expectFailure()
                .run("assembleDebug")

        Truth.assertThat(result.stdout).contains("error: cannot find symbol")
        Truth.assertThat(result.stdout)
                .contains("int resRef = android.support.constraint.R.attr.invalid_reference;")
    }
}