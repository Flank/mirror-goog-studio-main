/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.options.BooleanOption
import com.android.utils.XmlUtils
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets

class DynamicFeatureNamespaceTest {

    @JvmField
    @Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                applicationId = "com.example.test"
                dynamicFeatures.add(":feature")
            }
        }
        subProject(":feature") {
            plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
            android {
                defaultCompileSdk()
                applicationId = "com.example.test"
                namespace = "com.example.test.feature"
            }
            dependencies { implementation(project(":app")) }
        }
    }

    @Test
    fun `intermediate feature manifest should have feature's namespace as package`() {
        project.executor().run(":feature:processManifestDebugForFeature")

        val manifestFile =
            project.getSubproject("feature")
                .getIntermediateFile("metadata_feature_manifest", "debug", ANDROID_MANIFEST_XML)

        val document =
            XmlUtils.parseDocument(
                Files.asCharSource(manifestFile, StandardCharsets.UTF_8).read(),
                false
            )
        Truth.assertThat(document.documentElement.hasAttribute(ATTR_PACKAGE)).isTrue()
        Truth.assertThat(document.documentElement.getAttribute(ATTR_PACKAGE))
            .isEqualTo("com.example.test.feature")
    }

    @Test
    fun `app manifest should have applicationId as package`() {
        project.executor().with(BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES, true).run(":app:build")

        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        ApkSubject.assertThat(apk).hasApplicationId("com.example.test")
    }

}
