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

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.model.getAndroidProject
import com.android.build.gradle.integration.common.fixture.model.getVariantDependencies
import com.android.build.gradle.integration.common.fixture.model.toValueString
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class PrebuiltLintChecksModelTest {
    @get:Rule
    val project = createGradleProject {
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
            dependencies {
                lintChecks(localJar {
                    name = "lint-check.jar"
                    addClass("com/example/MainClass")
                })
            }
        }
    }

    private val result: ModelBuilderV2.FetchResult<ModelContainerV2> by lazy {
        project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")
    }

    @Test
    fun `test lintChecksJars in Lib model`() {
        val androidProject = result.getAndroidProject(":lib")

        Truth
            .assertThat(androidProject.lintChecksJars.map { it.toValueString(result.normalizer) })
            .containsExactly("{PROJECT}/lib/libs/lint-check.jar{F}")
    }
}

class SubProjectLintChecksModelTest {
    @get:Rule
    val project = createGradleProject {
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
            dependencies {
                lintChecks(project(":lint-check"))
            }
        }
        subProject(":lint-check") {
            plugins.add(PluginType.JAVA_LIBRARY)
            dependencies {
                implementation(localJar {
                    name = "local-lint.jar"
                    addClass("com/example/MainClass")
                })
                implementation(project(":lint-check-dependency"))
            }
        }
        subProject(":lint-check-dependency") {
            plugins.add(PluginType.JAVA_LIBRARY)
        }
    }

    private val result: ModelBuilderV2.FetchResult<ModelContainerV2> by lazy {
        project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")
    }

    @Test
    fun `test lintChecksJars in Lib model`() {
        val androidProject = result.getAndroidProject(":lib")

        Truth
            .assertThat(androidProject.lintChecksJars.map { it.toValueString(result.normalizer) })
            .containsExactly(
                "{PROJECT}/lint-check/build/libs/lint-check.jar{!}",
                "{PROJECT}/lint-check/libs/local-lint.jar{F}",
                "{PROJECT}/lint-check-dependency/build/libs/lint-check-dependency.jar{!}"
            )
    }
}

class AppAndLibWithLintPublishModelTest {
    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation(project(":lib"))
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
            dependencies {
                lintPublish(localJar {
                    name = "lint-publish.jar"
                    addClass("com/example/MainClass")
                })
            }
        }
    }

    private val result: ModelBuilderV2.FetchResult<ModelContainerV2> by lazy {
        project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")
    }

    @Test
    fun `test lint model in app dependency`() {
        val lib = result.container.globalLibraryMap?.libraries?.values?.singleOrNull {
            it.projectInfo?.let { info ->
                info.projectPath == ":lib" && info.attributes["org.gradle.usage"] == "java-api"
            } ?: false
        }

        Truth.assertWithMessage("lib Library instance").that(lib).isNotNull()

        Truth.assertThat(lib?.lintJar?.toValueString(result.normalizer)).isEqualTo(
            "{PROJECT}/lib/build/intermediates/lint_publish_jar/global/lint.jar{!}"
        )
    }

    @Test
    fun `check publish jar does not show up in lintChecks`() {
        val androidProject = result.getAndroidProject(":lib")

        Truth.assertThat(androidProject.lintChecksJars.isEmpty())
    }
}

class AppWithExternalLibraryWithLintJarModelTest {
    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation(
                    MavenRepoGenerator.Library(
                        mavenCoordinate = "com.example:example-aar:4.2",
                        packaging = "aar",
                        artifact =
                            generateAarWithContent(
                                packageName = "com.example.aar",
                                mainJar = TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/aar/AarClass")),
                                resources = mapOf("values/strings.xml" to """<resources><string name="aar_string">Aar String</string></resources>""".toByteArray()),
                                lintJar = TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/aar/LintChecks")),
                            )
                    )
                )
            }
        }
    }

    private val result: ModelBuilderV2.FetchResult<ModelContainerV2> by lazy {
        project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")
    }

    @Test
    fun `test lint model in app dependency`() {
        val lib = result.container.globalLibraryMap?.libraries?.values?.singleOrNull {
            it.libraryInfo?.let { info ->
                info.name == "example-aar"  && info.attributes["org.gradle.usage"] == "java-api"
            } ?: false
        }

        Truth.assertWithMessage("lib Library instance").that(lib).isNotNull()

        Truth.assertThat(lib?.lintJar?.toValueString(result.normalizer)).isEqualTo(
            "{GRADLE_CACHE}/{CHECKSUM}/transformed/example-aar-4.2/jars/lint.jar{F}"
        )
    }
}
