/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.builder.internal.packaging.IncrementalPackager.APP_METADATA_ENTRY_PATH
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.testutils.apk.Zip
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.function.Consumer
import kotlin.test.fail

/**
 * Tests for [AppMetadataTask]
 */
class AppMetadataTaskTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("\n\nandroid.dynamicFeatures = [':feature']\n\n")
            .withFile(
                "src/main/res/values/strings.xml",
                """
                    <resources>
                        <string name="feature_title">Dynamic Feature Title</string>
                    </resources>
                """.trimIndent()
            )

    private val feature =
        MinimalSubProject.dynamicFeature("com.example.feature")
            .apply {
                replaceFile(
                    TestSourceFile(
                        "src/main/AndroidManifest.xml",
                        """
                            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                xmlns:dist="http://schemas.android.com/apk/distribution"
                                package="com.example.app.dynamic.feature">
                                <dist:module
                                    dist:onDemand="true"
                                    dist:title="@string/feature_title">
                                    <dist:fusing dist:include="true" />
                                </dist:module>
                                <application />
                            </manifest>
                            """.trimIndent()
                    )
                )
            }

    private val lib = MinimalSubProject.lib("com.example.lib")

    @JvmField
    @Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":feature", feature)
                    .subproject(":lib", lib)
                    .dependency(feature, app)
                    .dependency(app, lib)
                    .build()
            ).create()

    @Test
    fun testNoAppMetadataInAar() {
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").getAar(
            "debug",
            Consumer { assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNull() }
        )
    }

    @Test
    fun testNoAppMetadataInDynamicFeatureApk() {
        project.executor().run(":feature:assembleDebug")
        project.getSubproject("feature").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNull()
        }
    }

    @Test
    fun testAppMetadataInApk() {
        project.executor().run(":app:assembleDebug")
        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNotNull()
        }
    }

    @Test
    fun testAppMetadataInBundle() {
        project.executor().run(":app:bundleDebug")
        val bundleFile =
            project.model()
                .fetchContainer(AppBundleProjectBuildOutput::class.java)
                .rootBuildModelMap[":app"]
                ?.getOutputByName("debug")
                ?.bundleFile
                ?: fail("Failed to find app bundle file.")
        Zip(bundleFile).use {
            assertThat(
                it.getEntry(
                    "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
                )
            ).isNotNull()
        }
    }
}
