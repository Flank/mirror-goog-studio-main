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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests in which java resources are merged from feature and dynamic-feature modules.
 */
class DynamicFeatureJavaResTest {

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild("""
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                    }
                }
                """)
            .withFile("src/main/resources/foo.txt", "lib")

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
            """
                            android {
                                dynamicFeatures = [':feature']
                                buildTypes {
                                    minified.initWith(buildTypes.debug)
                                    minified {
                                        minifyEnabled true
                                        proguardFiles getDefaultProguardFile('proguard-android.txt'),
                                                "proguard-rules.pro"
                                    }
                                }
                            }
                            """)
        .withFile("src/main/resources/foo.txt", "base")
        .withFile(
            "src/main/java/com/example/baseModule/EmptyClassToKeep.java",
            """package com.example.baseModule;
                public class EmptyClassToKeep {
                }""")
        .withFile(
            "proguard-rules.pro",
            """-keep public class com.example.baseModule.EmptyClassToKeep""")

    private val feature = MinimalSubProject.dynamicFeature("com.example.feature")
        .appendToBuild(
            """
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                    }
                }
                """)

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("""
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                    }
                    packagingOptions {
                        exclude "/foo.txt"
                    }
                }
                """)

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":baseModule", baseModule)
            .subproject(":feature", feature)
            .dependency(feature, baseModule)
            .dependency(feature, lib)
            .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(testApp)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN_GRADLE_6_6)
            .setTargetGradleVersion("6.6-20200609220026+0000")
            // b/157470515
            .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=1")
            .create()

    @Test
    fun `test duplicate java res from feature library dependency throws exception`() {
        val result = project.executor().expectFailure().run("assembleMinified")
        assertThat(result.failureMessage).contains(
            "More than one file was found with OS independent path 'foo.txt'"
        )
    }

    @Test
    fun `test java res from feature library dependency can be excluded`() {
        project.getSubproject(":feature")
            .buildFile
            .appendText("android.packagingOptions.exclude \"/foo.txt\"")
        project.executor().run("assembleMinified")
    }
}

