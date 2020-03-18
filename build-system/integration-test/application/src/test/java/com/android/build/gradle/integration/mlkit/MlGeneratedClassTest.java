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

package com.android.build.gradle.integration.mlkit;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for mlkit generated model class. */
public class MlGeneratedClassTest {

    @Rule public GradleTestProject project;

    public MlGeneratedClassTest() {
        this.project =
                GradleTestProject.builder()
                        .fromTestApp(MinimalSubProject.Companion.app("com.android.app"))
                        .create();
    }

    @Before
    public void setUp() throws IOException {
        File buildFile = project.getBuildFile();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.buildFeatures.mlModelBinding true");
        TestFileUtils.appendToFile(
                buildFile,
                "dependencies {\n"
                        + "    implementation 'androidx.appcompat:appcompat:1.1.0'\n"
                        + "    implementation 'org.apache.commons:commons-compress:1.18'\n"
                        + "    implementation 'org.tensorflow:tensorflow-lite:1.12.0'\n"
                        + "    implementation 'org.tensorflow:tensorflow-lite-support:0.0.0-nightly'\n"
                        + "}");
        TestFileUtils.searchAndReplace(
                buildFile, "defaultConfig.minSdkVersion 14", "defaultConfig.minSdkVersion 24");

        File gradlePropertiesFile = project.getGradlePropertiesFile();
        TestFileUtils.appendToFile(gradlePropertiesFile, "android.useAndroidX=true");
    }

    @Test
    public void testMobileNetModelClass() throws IOException, InterruptedException {
        verifyModelClass("mobilenet_quant_metadata.tflite");
    }

    @Test
    public void testMobileNetModelClassWithoutMetadata() throws IOException, InterruptedException {
        verifyModelClass("mobilenet_quant_no_metadata.tflite");
    }

    @Test
    public void testDeepLabModelClass() throws IOException, InterruptedException {
        verifyModelClass("deeplab_image_seg_metadata.tflite");
    }

    @Test
    public void testInceptionModelClass() throws IOException, InterruptedException {
        verifyModelClass("inception_quant_metadata.tflite");
    }

    @Test
    public void testMnasnetModelClass() throws IOException, InterruptedException {
        verifyModelClass("mnasnet_metadta.tflite");
    }

    @Test
    public void testSsdOdtModelClass() throws IOException, InterruptedException {
        verifyModelClass("ssd_mobilenet_odt_metadata.tflite");
    }

    @Test
    public void testStyleTransferModelClass() throws IOException, InterruptedException {
        verifyModelClass("style_transfer_quant_metadata.tflite");
    }

    private void verifyModelClass(String modelFileName) throws IOException, InterruptedException {
        // Add model file to ml folder
        File modelFile = project.file("src/main/ml/model.tflite");
        FileUtils.copyFile(
                TestUtils.getWorkspaceFile(
                        "prebuilts/tools/common/mlkit/testData/" + modelFileName),
                modelFile);

        project.executor().run(":assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).containsClass("Lcom/android/app/ml/Model;");
    }
}
