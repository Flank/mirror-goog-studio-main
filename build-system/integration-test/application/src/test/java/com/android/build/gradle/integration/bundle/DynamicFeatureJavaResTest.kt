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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests in which java resources from dynamic-feature modules are packaged.
 */
class DynamicFeatureJavaResTest {

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        buildTypes {
                            minified.initWith(buildTypes.debug)
                        }
                    }""".trimIndent()
            )
            .withFile("src/main/resources/collide.txt", "lib")
            .withFile("src/main/resources/pickFirst.txt", "lib")
            .withFile(
                "src/main/resources/META-INF/services/com.example.Service",
                "com.example.lib.impl.LibService"
            )

    private val baseModule =
        MinimalSubProject.app("com.example.baseModule")
            .appendToBuild(
                """
                    android {
                        dynamicFeatures = [':dynamicFeature']
                        buildTypes {
                            minified.initWith(buildTypes.debug)
                            minified {
                                minifyEnabled true
                            }
                        }
                    }""".trimIndent()
            )
        .withFile("src/main/resources/collide.txt", "base")
        .withFile(
            "src/main/resources/META-INF/services/com.example.Service",
            "com.example.baseModule.impl.BaseModuleService"
        )

    private val dynamicFeature =
        MinimalSubProject.dynamicFeature("com.example.dynamicFeature")
            .appendToBuild(
                """
                    android {
                        buildTypes {
                            minified.initWith(buildTypes.debug)
                        }
                        packagingOptions {
                            exclude "collide.txt"
                            pickFirst "pickFirst.txt"
                        }
                    }""".trimIndent()
            )
            .withFile("src/main/resources/pickFirst.txt", "dynamicFeature")
            .withFile(
                "src/main/resources/META-INF/services/com.example.Service",
                "com.example.dynamicFeature.impl.DynamicFeatureService"
            )
            .withFile(
                "src/main/resources/META-INF/services/com.example.feature.Service",
                "com.example.dynamicFeature.impl.DynamicFeatureService"
            )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":baseModule", baseModule)
            .subproject(":dynamicFeature", dynamicFeature)
            .dependency(dynamicFeature, baseModule)
            .dependency(dynamicFeature, lib)
            .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(testApp)
            .create()

    @Test
    fun testDuplicateJavaResThrowsException() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":dynamicFeature").buildFile, "exclude \"collide.txt\"", ""
        )
        val result = project.executor().expectFailure().run("assembleMinified")
        assertThat(result.failureMessage).contains(
            "Multiple dynamic-feature and/or base APKs will contain entries "
                    + "with the same path, 'collide.txt'"
        )
    }

    @Test
    fun testJavaResourcePackaging() {
        project.executor().run("assembleMinified")
        project.getSubproject("dynamicFeature").getApk(apkType).use { apk ->
            assertThat(apk.file).exists()
            assertThat(apk).containsJavaResourceWithContent("pickFirst.txt", "dynamicFeature")
            assertThat(apk).doesNotContainJavaResource("META-INF/services/com.example.Service")
            assertThat(apk).doesNotContainJavaResource(
                "META-INF/services/com.example.feature.Service"
            )
        }
        project.getSubproject("baseModule").getApk(apkType).use { apk ->
            assertThat(apk.file).exists()
            assertThat(apk).containsJavaResourceWithContent("collide.txt", "base")
            assertThat(apk).containsJavaResourceWithContent(
                "META-INF/services/com.example.Service",
                """
                    com.example.baseModule.impl.BaseModuleService
                    com.example.dynamicFeature.impl.DynamicFeatureService
                    com.example.lib.impl.LibService
                    """.trimIndent()
            )
            // All services go to base the APK, even if they come from only a dynamic feature.
            assertThat(apk).containsJavaResourceWithContent(
                "META-INF/services/com.example.feature.Service",
                "com.example.dynamicFeature.impl.DynamicFeatureService"
            )
        }
    }
}

val apkType = object : GradleTestProject.ApkType {
    override val buildType: String = "minified"
    override val testName: String? = null
    override val isSigned: Boolean = true
}

