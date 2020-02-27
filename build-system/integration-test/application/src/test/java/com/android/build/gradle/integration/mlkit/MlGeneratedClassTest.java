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
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
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
                buildFile,
                "dependencies {\n"
                        + "    implementation 'org.apache.commons:commons-compress:1.18'\n"
                        + "    implementation 'org.tensorflow:tensorflow-lite:1.12.0'\n"
                        + "    implementation 'org.tensorflow:tensorflow-lite-support:0.0.0-nightly'\n"
                        + "}");
        TestFileUtils.searchAndReplace(
                buildFile, "defaultConfig.minSdkVersion 14", "defaultConfig.minSdkVersion 24");
    }

    @Test
    public void testModelClass() throws IOException, InterruptedException, ProcessException {
        // Add model file to assets
        File modelFile = project.file("src/main/assets/model.tflite");
        FileUtils.copyFile(
                TestUtils.getWorkspaceFile(
                        "prebuilts/tools/common/mlkit/testData/mobilenet_quant_metadata.tflite"),
                modelFile);

        project.executor().with(BooleanOption.ENABLE_MLKIT, true).run(":assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).containsClass("Lcom/android/app/ml/Model;");
    }

    @Test
    public void testModelClassWithoutMetadata()
            throws IOException, InterruptedException, ProcessException {
        // Add model file to assets
        File modelFile = project.file("src/main/assets/model.tflite");
        FileUtils.copyFile(
                TestUtils.getWorkspaceFile(
                        "prebuilts/tools/common/mlkit/testData/mobilenet_quant_no_metadata.tflite"),
                modelFile);

        project.executor().with(BooleanOption.ENABLE_MLKIT, true).run(":assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(apk).containsClass("Lcom/android/app/ml/Model;");
    }
}
