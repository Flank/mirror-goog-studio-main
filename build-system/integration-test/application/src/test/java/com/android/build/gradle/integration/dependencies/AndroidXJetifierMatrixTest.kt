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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ModelContainer
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.IssueReporter
import com.android.builder.model.AndroidProject
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for detection of different issues relating to different combinations of the
 * [BooleanOption.USE_ANDROID_X] and [BooleanOption.ENABLE_JETIFIER] properties.
 */
class AndroidXJetifierMatrixTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(MinimalSubProject.app("com.example.application"))
            .create()

    private fun addAndroidXDependencies() {
        project.projectDir.resolve("mavenRepo").also {
            FileUtils.mkdirs(it)
            MavenRepoGenerator(
                    listOf(
                            // Set up the libraries such that they have some shared dependencies to
                            // check that AGP can handle that case (it should show only one path for
                            // each dependency in the error message).
                            MavenRepoGenerator.Library(
                                    "depends-on-androidx:lib1:1.0",
                                    TestInputsGenerator.jarWithEmptyClasses(listOf()),
                                    "androidx.annotation:annotation:$ANDROIDX_VERSION"
                            ),
                            MavenRepoGenerator.Library(
                                    "depends-on-androidx:lib2:1.0",
                                    TestInputsGenerator.jarWithEmptyClasses(listOf()),
                                    "androidx.annotation:annotation:$ANDROIDX_VERSION",
                                    "androidx.collection:collection:$ANDROIDX_VERSION",
                            )
                    )
            ).generate(it.toPath())
        }
        TestFileUtils.appendToFile(
                project.buildFile,
                """
                repositories {
                    maven { url 'mavenRepo' }
                }
                dependencies {
                    implementation 'depends-on-androidx:lib1:1.0'
                    implementation 'depends-on-androidx:lib2:1.0'
                }
                """.trimIndent()
        )
    }

    private fun addSupportLibDependencies() {
        project.projectDir.resolve("mavenRepo").also {
            FileUtils.mkdirs(it)
            MavenRepoGenerator(
                    listOf(
                            // Set up the libraries such that they have some shared dependencies to
                            // check that AGP can handle that case (it should show only one path for
                            // each dependency in the error message).
                            MavenRepoGenerator.Library(
                                    "depends-on-support-lib:lib1:1.0",
                                    TestInputsGenerator.jarWithEmptyClasses(listOf()),
                                    "com.android.support:support-annotations:$SUPPORT_LIB_VERSION",
                            ),
                            MavenRepoGenerator.Library(
                                    "depends-on-support-lib:lib2:1.0",
                                    TestInputsGenerator.jarWithEmptyClasses(listOf()),
                                    "com.android.support:support-annotations:$SUPPORT_LIB_VERSION",
                                    "com.android.support:collections:$SUPPORT_LIB_VERSION",
                            )
                    )
            ).generate(it.toPath())
        }
        TestFileUtils.appendToFile(
                project.buildFile,
                """
                repositories {
                    maven { url 'mavenRepo' }
                }
                dependencies {
                    implementation 'depends-on-support-lib:lib1:1.0'
                    implementation 'depends-on-support-lib:lib2:1.0'
                }
                """.trimIndent()
        )
    }

    private fun expectSyncIssue(
            model: ModelContainer<AndroidProject>,
            type: IssueReporter.Type,
            severity: IssueReporter.Severity,
            message: String,
            data: String? = null
    ) {
        val syncIssues = model.onlyModelSyncIssues
        assertThat(syncIssues).hasSize(1)
        val syncIssue = syncIssues.single()

        assertThat(syncIssue.type).isEqualTo(type.type)
        assertThat(syncIssue.severity).isEqualTo(severity.severity)
        assertThat(syncIssue.message).isEqualTo(message)
        data?.let { assertThat(syncIssue.data).isEqualTo(it) }
    }

    @Test
    fun `AndroidX=false, Jetifier=false, AndroidX dependencies present, expect sync issue`() {
        addAndroidXDependencies()

        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        expectSyncIssue(
                model,
                IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                IssueReporter.Severity.ERROR,
                message = "Configuration `debugRuntimeClasspath` contains AndroidX dependencies, but the `android.useAndroidX` property is not enabled, which may cause runtime issues.\n" +
                        "Set `android.useAndroidX=true` in the `gradle.properties` file and retry.\n" +
                        "The following AndroidX dependencies are detected:\n" +
                        "debugRuntimeClasspath -> depends-on-androidx:lib1:1.0 -> androidx.annotation:annotation:$ANDROIDX_VERSION\n" +
                        "debugRuntimeClasspath -> depends-on-androidx:lib2:1.0 -> androidx.collection:collection:$ANDROIDX_VERSION",
                data = "debugRuntimeClasspath -> depends-on-androidx:lib1:1.0 -> androidx.annotation:annotation:$ANDROIDX_VERSION," +
                        "debugRuntimeClasspath -> depends-on-androidx:lib2:1.0 -> androidx.collection:collection:$ANDROIDX_VERSION"
        )
    }

    @Test
    fun `AndroidX=false, Jetifier=false, AndroidX dependencies not present, expect no issues`() {
        addSupportLibDependencies()

        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        assertThat(model.onlyModelSyncIssues).isEmpty()
    }

    @Test
    fun `AndroidX=false, Jetifier=true, expect sync issue`() {
        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, true)
                .ignoreSyncIssues().fetchAndroidProjects()
        expectSyncIssue(
                model,
                type = IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                severity = IssueReporter.Severity.ERROR,
                message = "AndroidX must be enabled when Jetifier is enabled. To resolve, set" +
                        " ${BooleanOption.USE_ANDROID_X.propertyName}=true" +
                        " in your gradle.properties file."
        )
    }

    @Test
    fun `AndroidX=true, Jetifier=false, support library dependencies present, expect sync issue`() {
        addSupportLibDependencies()

        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, true)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        expectSyncIssue(
                model,
                IssueReporter.Type.ANDROID_X_PROPERTY_NOT_ENABLED,
                IssueReporter.Severity.WARNING,
                message = "Your project has set `android.useAndroidX=true`, but configuration `debugRuntimeClasspath` still contains legacy support libraries, which may cause runtime issues.\n" +
                        "This behavior will not be allowed in Android Gradle plugin 8.0.\n" +
                        "Please use only AndroidX dependencies or set `android.enableJetifier=true` in the `gradle.properties` file to migrate your project to AndroidX (see https://developer.android.com/jetpack/androidx/migrate for more info).\n" +
                        "The following legacy support libraries are detected:\n" +
                        "debugRuntimeClasspath -> depends-on-support-lib:lib1:1.0 -> com.android.support:support-annotations:28.0.0\n" +
                        "debugRuntimeClasspath -> depends-on-support-lib:lib2:1.0 -> com.android.support:collections:28.0.0",
                data = "debugRuntimeClasspath -> depends-on-support-lib:lib1:1.0 -> com.android.support:support-annotations:$SUPPORT_LIB_VERSION," +
                        "debugRuntimeClasspath -> depends-on-support-lib:lib2:1.0 -> com.android.support:collections:$SUPPORT_LIB_VERSION"
        )
    }

    @Test
    fun `AndroidX=true, Jetifier=false, support library dependencies not present, expect no issues`() {
        addAndroidXDependencies()

        val model = project.model()
                .with(BooleanOption.USE_ANDROID_X, true)
                .with(BooleanOption.ENABLE_JETIFIER, false)
                .ignoreSyncIssues().fetchAndroidProjects()
        assertThat(model.onlyModelSyncIssues).isEmpty()
    }
}
