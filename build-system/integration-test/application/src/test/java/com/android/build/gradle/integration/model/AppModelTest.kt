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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class HelloWorldAppModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                minSdk = 14
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVersions(goldenFile = "Versions")
        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}

class ApplicationIdInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                applicationId = "customized.application.id"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DisabledAidlInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    aidl = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class EnabledRenderScriptInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    renderScript = true
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DisabledResValuesInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    resValues = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DisabledShadersInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    shaders = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DisabledBuildConfigInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    buildConfig = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class EnabledMlModelBindingInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    mlModelBinding = true
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DefaultBuildTypeAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildTypes {
                    named("debug") {
                        isDefault = true
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DefaultFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("flavorA") {
                        dimension = "foo"
                    }
                    named("flavorB") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                productFlavors {
                    named("flavorA") {
                        isDefault = true
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class AarApiJarModelTest : ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            dependencies {
                implementation(
                    MavenRepoGenerator.Library(
                        "com.example:myaar:1",
                        "aar",
                        generateAarWithContent(
                            packageName = "com.example.myaar",
                            mainJar = TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/MainClass")),
                            secondaryJars = mapOf(
                                "impl1.jar" to TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/Impl1")),
                                "impl2.jar" to TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/Impl2"))
                            ),
                            apiJar = TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/ApiClass"))
                        )
                    )
                )
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    },
    variantName = "debug"
) {

    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }

    @Test
    fun `test dependencies model`() {
        compareVariantDependenciesWith(goldenFileSuffix = "WithAar")
    }
}

class AndroidTestNamespaceWithCustomAppIdTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = true)
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                applicationId = "com.custom.appid"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test AndroidProject model`() {
        // TODO(b/176931684) Once we stop using applicationId for the androidTestNamespace then
        //  this will need to be changed as the namespace will be not be impacted by the
        //  DSL-set appId
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class AndroidTestNamespaceWithCustomNamespaceTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                namespace = "com.custom.namespace"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test AndroidProject model`() {
        // TODO(b/176931684) Once we stop using applicationId for the androidTestNamespace then
        //  this will need to be changed as the namespace will be impacted by the DSL-set ns
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class LintChecksInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            dependencies {
                lintChecks(localJar {
                    name = "lint-checks.jar"
                    addClass("com/example/MainClass")
                })
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }
}

class ResValuesInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildTypes {
                    named("debug") {
                        resValue("string", "foo", "val")
                        resValue("drawable", "foo", "val")
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

/**
 * Tests case where there's an artifact relocated via Gradle metadata
 */
class RelocatedArtifactTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}

class CompileSdkViaSettingsInAppModelTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    }

    @Test
    fun `test compileTarget`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val androidDsl = result.container.getProject().androidDsl
            ?: throw RuntimeException("Failed to get AndroidDsl Model")

        Truth
            .assertWithMessage("compile target hash")
            .that(androidDsl.compileTarget)
            .isEqualTo("android-$DEFAULT_COMPILE_SDK_VERSION")
    }
}

/**
 * This tests uses a reference project where the compile SDK is set through the settings
 * plugin to 24.
 *
 * Then the project is changed to override this at the project level with the current default
 * API level.
 */
class CompileSdkViaSettingsOverriddenInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                compileSdk = 24
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                compileSdk = GradleTestProject.DEFAULT_COMPILE_SDK_VERSION.toInt()
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class MinSdkViaSettingsInAppModelTest {
    @get:Rule
    val project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
            android {
                minSdk = 23
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test minSdkVersion`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        val androidDsl = result.container.getProject().androidDsl
            ?: throw RuntimeException("Failed to get AndroidDsl Model")

        Truth
            .assertWithMessage("minSdkVersion")
            .that(androidDsl.defaultConfig.minSdkVersion)
            .isNotNull()

        Truth
            .assertWithMessage("minSdkVersion.apiLevel")
            .that(androidDsl.defaultConfig.minSdkVersion?.apiLevel)
            .isEqualTo(23)

        Truth
            .assertWithMessage("minSdkVersion.codename")
            .that(androidDsl.defaultConfig.minSdkVersion?.codename)
            .isNull()
    }
}

/** Regression test for http://b/229298359. */
class DependencyWithoutFileWithDependenciesTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile {
                """
                      repositories {
                        maven {
                          url { '../repo' }
                        }
                      }
                      dependencies {
                        testImplementation("com.foo:bar:1.0") {
                          capabilities {
                            requireCapability("com.foo:bar-custom:1.0")
                          }
                        }
                      }
                """.trimIndent()
            }
        }
        subProject(":bar") {
            plugins.add(PluginType.JAVA_LIBRARY)
            plugins.add(PluginType.MAVEN_PUBLISH)
            appendToBuildFile {
                """
                    group = "com.foo"
                    version = "1.0"

                    Configuration customCapability = configurations.create("customCapability")
                    customCapability.setCanBeConsumed(true)
                    customCapability.setCanBeResolved(false)
                    customCapability.attributes.attribute(
                      TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                      objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM)
                    )
                    customCapability.outgoing.capability("com.foo:bar-custom:1.0")
                    dependencies.add("customCapability", 'androidx.annotation:annotation:$ANDROIDX_VERSION')
                    components.java.addVariantsFromConfiguration(customCapability) { mapToOptional() }

                    publishing {
                      repositories {
                        maven { url = '../repo' }
                      }
                      publications {
                        mavenJava(MavenPublication) {
                          from components.java
                        }
                      }
                    }
                """.trimIndent()
            }
        }
    }

    @Test
    fun `test models`() {
        project.executor().run(":bar:publish")
        val result = project.modelV2()
            .with(BooleanOption.USE_ANDROID_X, true)
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}

class AppWithUseLibraryModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                useLibrary("android.car")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}
