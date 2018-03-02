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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import java.io.IOException

class DynamicAppTest {
    companion object {
        @ClassRule
        @JvmField
        val project: GradleTestProject = GradleTestProject.builder()
            .fromTestProject("dynamicApp")
            .withoutNdk()
            .create()
    }

    @Test
    @Throws(IOException::class)
    fun testModel() {
        val rootBuildModelMap = project.model().fetchAndroidProjects().rootBuildModelMap

        val appModel = rootBuildModelMap[":app"]
        Truth.assertThat(appModel).named("app model").isNotNull()
        Truth.assertThat(appModel!!.dynamicFeatures)
            .named("feature list in app model")
            .containsExactly(":feature1")

        val featureModel = rootBuildModelMap[":feature1"]
        Truth.assertThat(featureModel).named("feature model").isNotNull()
        Truth.assertThat(featureModel!!.projectType)
            .named("feature model type")
            .isEqualTo(AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE)
    }

    @Test
    fun testBundle() {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
        project.execute("bundle:bundle")

        val bundleFile = File(project.getSubproject("bundle").buildDir, "bundle.aab")
        FileSubject.assertThat(bundleFile).exists()

        val zipFile = Zip(bundleFile)
        Truth.assertThat(zipFile.entries.map { it.toString() }).containsExactly(
            "/BundleManifest.pb",
            "/base/dex/classes.dex",
            "/base/manifest/AndroidManifest.xml",
            "/base/res/layout/base_layout.xml",
            "/base/resources.pb",
            "/feature1/dex/classes.dex",
            "/feature1/manifest/AndroidManifest.xml",
            "/feature1/res/layout/feature_layout.xml",
            "/feature1/resources.pb")
    }


    @Test
    fun testBundleFromApp() {
        project.execute("app:bundleDebug")

        //TODO: update model with this stuff?
        val bundleFile = FileUtils.join(project.getSubproject("app").buildDir,
            "outputs",
            "bundle",
            "debug",
            "bundle.aab")
        FileSubject.assertThat(bundleFile).exists()

        val zipFile = Zip(bundleFile)
        Truth.assertThat(zipFile.entries.map { it.toString() }).containsExactly(
            "/BundleManifest.pb",
            "/base/dex/classes.dex",
            "/base/manifest/AndroidManifest.xml",
            "/base/res/layout/base_layout.xml",
            "/base/resources.pb",
            "/feature1/dex/classes.dex",
            "/feature1/manifest/AndroidManifest.xml",
            "/feature1/res/layout/feature_layout.xml",
            "/feature1/resources.pb")

    }
}
