/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.testutils.apk.Apk
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static org.junit.Assert.assertEquals
/**
 * MultiAPK test where densities are obtained automatically.
 */
@CompileStatic
class AutoDensitySplitTest {
    static GetAndroidModelAction.ModelContainer<AndroidProject> model

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("densitySplit")
            .create()

    @BeforeClass
    static void setUp() {
        project.getBuildFile() << """android {
          splits {
            density {
              enable true
              auto true
              compatibleScreens 'small', 'normal', 'large', 'xlarge'
            }
          }
        }"""
        model = project.executeAndReturnModel("clean", "assembleDebug")
    }

    @AfterClass
    static void cleanUp() {
        project = null
        model = null
    }

    @Test
    void testPackaging() {
        for (Variant variant : model.getOnlyModel().getVariants()) {
            AndroidArtifact mainArtifact = variant.getMainArtifact()
            if (!variant.getBuildType().equalsIgnoreCase("Debug")) {
                continue
            }
            for (AndroidArtifactOutput output : mainArtifact.getOutputs()) {
                System.out.println(output)
            }
            assertEquals(5, mainArtifact.getOutputs().size())

            Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG)
            assertThat(mdpiApk).contains("res/drawable-mdpi-v4/other.png")
        }
    }

    @Test
    void "check version code in apk"() {
        Apk universalApk = project.getApk("universal", GradleTestProject.ApkType.DEBUG)
        assertThat(universalApk).hasVersionCode(112)
        assertThat(universalApk).hasVersionName("version 112")

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG)
        assertThat(mdpiApk).hasVersionCode(212)
        assertThat(mdpiApk).hasVersionName("version 212")

        Apk hdpiApk = project.getApk("hdpi", GradleTestProject.ApkType.DEBUG)
        assertThat(hdpiApk).hasVersionCode(312)
        assertThat(hdpiApk).hasVersionName("version 312")

        Apk xhdpiApk = project.getApk("xhdpi", GradleTestProject.ApkType.DEBUG)
        assertThat(xhdpiApk).hasVersionCode(412)
        assertThat(xhdpiApk).hasVersionName("version 412")

        Apk xxhdiApk = project.getApk("xxhdpi", GradleTestProject.ApkType.DEBUG)
        assertThat(xxhdiApk).hasVersionCode(512)
        assertThat(xxhdiApk).hasVersionName("version 512")
    }
}
