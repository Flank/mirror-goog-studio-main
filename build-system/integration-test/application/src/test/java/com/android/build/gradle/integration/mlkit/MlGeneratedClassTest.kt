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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.util.ParamUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for mlkit generated model class. */
public class MlGeneratedClassTest {

    private static final String MODEL = "Lcom/android/app/ml/Model;";
    private static final String MODEL_OUTPUT = "Lcom/android/app/ml/Model$Outputs;";
    private static final String TENSOR_IMAGE = "Lorg/tensorflow/lite/support/image/TensorImage;";
    private static final String TENSOR_BUFFER =
            "Lorg/tensorflow/lite/support/tensorbuffer/TensorBuffer;";
    private static final String TENSOR_LABEL = "Lorg/tensorflow/lite/support/label/TensorLabel;";

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
                buildFile, "defaultConfig.minSdkVersion 14", "defaultConfig.minSdkVersion 19");

        File gradlePropertiesFile = project.getGradlePropertiesFile();
        TestFileUtils.appendToFile(gradlePropertiesFile, "android.useAndroidX=true");
    }

    @Test
    public void testMobileNetModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getProbabilityAsTensorLabel", TENSOR_LABEL));
        verifyModelClass("mobilenet_quant_metadata.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testMobileNetModelClassWithoutMetadata() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_BUFFER));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getOutputFeature0AsTensorBuffer", TENSOR_BUFFER));
        verifyModelClass("mobilenet_quant_no_metadata.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testDeepLabModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getSegmentationmasksAsTensorBuffer", TENSOR_BUFFER));
        verifyModelClass("deeplab_image_seg_metadata.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testInceptionModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getProbabilityAsTensorLabel", TENSOR_LABEL));
        verifyModelClass("inception_quant_metadata.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testMnasnetModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getProbabilityAsTensorLabel", TENSOR_LABEL));
        verifyModelClass("mnasnet_metadta.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testSsdOdtModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getClassesAsTensorBuffer", TENSOR_BUFFER),
                        createOutputGetterMethodReference(
                                "getLocationsAsTensorBuffer", TENSOR_BUFFER),
                        createOutputGetterMethodReference(
                                "getNumberofdetectionsAsTensorBuffer", TENSOR_BUFFER),
                        createOutputGetterMethodReference(
                                "getScoresAsTensorBuffer", TENSOR_BUFFER));
        verifyModelClass("ssd_mobilenet_odt_metadata.tflite", modelMethods, outputsMethods);
    }

    @Test
    public void testStyleTransferModelClass() throws IOException, InterruptedException {
        Set<MethodReference> modelMethods =
                ImmutableSet.of(
                        createNewInstanceMethodReference(),
                        createProcessMethodReference(TENSOR_IMAGE + TENSOR_BUFFER));
        Set<MethodReference> outputsMethods =
                ImmutableSet.of(
                        createOutputGetterMethodReference(
                                "getStyledimageAsTensorBuffer", TENSOR_BUFFER));
        verifyModelClass("style_transfer_quant_metadata.tflite", modelMethods, outputsMethods);
    }

    private static MethodReference createNewInstanceMethodReference() {
        return new ImmutableMethod(
                MODEL,
                "newInstance",
                ParamUtil.parseParamString("Landroid/content/Context;"),
                "Lcom/android/app/ml/Model;",
                0,
                null,
                null);
    }

    private static MethodReference createProcessMethodReference(String params) {
        return new ImmutableMethod(
                MODEL, "process", ParamUtil.parseParamString(params), MODEL_OUTPUT, 0, null, null);
    }

    private static MethodReference createOutputGetterMethodReference(
            String name, String returnType) {
        return new ImmutableMethod(
                MODEL_OUTPUT, name, ParamUtil.parseParamString(""), returnType, 0, null, null);
    }

    private void verifyModelClass(
            String modelFileName,
            Set<MethodReference> expectedModelMethods,
            Set<MethodReference> expectedOutputMethods)
            throws IOException, InterruptedException {
        // Add model file to ml folder
        File modelFile = project.file("src/main/ml/model.tflite");
        FileUtils.copyFile(
                TestUtils.getWorkspaceFile(
                        "prebuilts/tools/common/mlkit/testData/models/" + modelFileName),
                modelFile);
        project.executor().run(":assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        Truth.assertThat(apk.getClass(MODEL).getMethods())
                .containsAtLeastElementsIn(expectedModelMethods);
        Truth.assertThat(apk.getClass(MODEL_OUTPUT).getMethods())
                .containsAtLeastElementsIn(expectedOutputMethods);
    }
}
