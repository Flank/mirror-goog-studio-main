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
package com.android.build.gradle.integration.mlkit

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject.Companion.app
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestUtils
import com.android.zipflinger.ZipArchive
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.apache.commons.io.FileUtils
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.util.ParamUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.zip.ZipEntry

/** Assemble tests for mlkit generated model class.  */
class MlGeneratedClassTest {
    @JvmField
    @Rule
    val project: GradleTestProject = builder()
        .fromTestApp(app("com.android.app"))
        .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val buildFile = project.buildFile
        TestFileUtils.appendToFile(buildFile, "android.buildFeatures.mlModelBinding true")
        TestFileUtils.appendToFile(
            buildFile,
            """
                dependencies {
                    implementation 'androidx.appcompat:appcompat:1.1.0'
                    implementation 'org.apache.commons:commons-compress:1.18'
                    implementation 'org.tensorflow:tensorflow-lite:1.12.0'
                    implementation 'org.tensorflow:tensorflow-lite-support:0.0.0-nightly'
                }
            """
        )
        TestFileUtils.searchAndReplace(
            buildFile, "defaultConfig.minSdkVersion 14", "defaultConfig.minSdkVersion 19"
        )
        val gradlePropertiesFile = project.gradlePropertiesFile
        TestFileUtils.appendToFile(gradlePropertiesFile, "android.useAndroidX=true")
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testMobileNetModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                )
            )
        verifyModelClass("mobilenet_quant_metadata.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testMobileNetModelClass_subFolder() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(MODEL_SUB),
                createNewInstanceWithOptionsMethodReference(MODEL_SUB),
                createProcessMethodReference(TENSOR_IMAGE, MODEL_SUB, MODEL_OUTPUT_SUB)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    MODEL_OUTPUT_SUB,
                    "getProbabilityAsCategoryList", LIST
                )
            )
        verifyModelClass(
            "mobilenet_quant_metadata.tflite",
            modelMethods,
            outputsMethods,
            true
        )
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testMobileNetModelClassWithoutMetadata() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_BUFFER)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getOutputFeature0AsTensorBuffer", TENSOR_BUFFER
                )
            )
        verifyModelClass("mobilenet_quant_no_metadata.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testDeepLabModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getSegmentationMasksAsTensorBuffer",
                    TENSOR_BUFFER
                )
            )
        verifyModelClass("deeplab_image_seg_metadata.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testInceptionModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                )
            )
        verifyModelClass("inception_quant_metadata.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testMnasnetModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                )
            )
        verifyModelClass("mnasnet_metadta.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testSsdOdtModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getClassesAsTensorBuffer", TENSOR_BUFFER
                ),
                createOutputGetterMethodReference(
                    "getLocationsAsTensorBuffer", TENSOR_BUFFER
                ),
                createOutputGetterMethodReference(
                    "getNumberOfDetectionsAsTensorBuffer", TENSOR_BUFFER
                ),
                createOutputGetterMethodReference(
                    "getScoresAsTensorBuffer", TENSOR_BUFFER
                )
            )
        verifyModelClass("ssd_mobilenet_odt_metadata.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testStyleTransferModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE + TENSOR_BUFFER)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getStyledImageAsTensorImage", TENSOR_IMAGE
                )
            )
        verifyModelClass("style_transfer_quant_metadata.tflite", modelMethods, outputsMethods)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun verifyModelClass(
        modelFileName: String,
        expectedModelMethods: Set<MethodReference>,
        expectedOutputMethods: Set<MethodReference>
    ) {
        verifyModelClass(
            modelFileName,
            expectedModelMethods,
            expectedOutputMethods,
            false
        )
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun verifyModelClass(
        modelFileName: String,
        expectedModelMethods: Set<MethodReference>,
        expectedOutputMethods: Set<MethodReference>,
        verifySubFolder: Boolean
    ) {
        val dstModelFilePath = if(verifySubFolder) "src/main/ml/sub/mobilenet_model.tflite" else "src/main/ml/model.tflite"
        val modelClass = if(verifySubFolder) MODEL_SUB else MODEL
        val modelOutputClass = if(verifySubFolder) MODEL_OUTPUT_SUB else MODEL_OUTPUT
        val apkModelFile = if(verifySubFolder) "assets/sub/mobilenet_model.tflite" else "assets/model.tflite"

        // Add model file to ml folder
        val modelFile = project.file(dstModelFilePath)
        FileUtils.copyFile(
            TestUtils.getWorkspaceFile(
                "prebuilts/tools/common/mlkit/testData/models/$modelFileName"
            ),
            modelFile
        )
        project.executor().run(":assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk.getClass(modelClass)!!.methods).containsAtLeastElementsIn(expectedModelMethods)
        assertThat(apk.getClass(modelOutputClass)!!.methods).containsAtLeastElementsIn(
            expectedOutputMethods
        )

        // Check model.tflite is uncompressed (Issue 152875817)
        val entry = ZipArchive.listEntries(apk.file.toFile())[apkModelFile]
        assertThat(entry?.compressionFlag).isEqualTo(ZipEntry.STORED)
    }

    companion object {
        private const val MODEL = "Lcom/android/app/ml/Model;"
        private const val MODEL_SUB = "Lcom/android/app/ml/MobilenetModel219;"
        private const val MODEL_OUTPUT = "Lcom/android/app/ml/Model\$Outputs;"
        private const val MODEL_OUTPUT_SUB = "Lcom/android/app/ml/MobilenetModel219\$Outputs;"
        private const val TENSOR_IMAGE = "Lorg/tensorflow/lite/support/image/TensorImage;"
        private const val TENSOR_BUFFER =
            "Lorg/tensorflow/lite/support/tensorbuffer/TensorBuffer;"
        private const val LIST = "Ljava/util/List;"
        private const val CONTEXT = "Landroid/content/Context;"
        private const val MODEL_OPTIONS = "Lorg/tensorflow/lite/support/model/Model\$Options;"

        private fun createNewInstanceMethodReference(): MethodReference {
            return createNewInstanceMethodReference(MODEL)
        }

        private fun createNewInstanceMethodReference(model: String): MethodReference {
            return ImmutableMethod(
                model,
                "newInstance",
                ParamUtil.parseParamString(CONTEXT),
                model,
                0,
                null,
                null
            )
        }

        private fun createNewInstanceWithOptionsMethodReference(model: String): MethodReference {
            return ImmutableMethod(
                model,
                "newInstance",
                ParamUtil.parseParamString(CONTEXT+ MODEL_OPTIONS),
                model,
                0,
                null,
                null
            )
        }

        private fun createProcessMethodReference(params: String): MethodReference {
            return createProcessMethodReference(params, MODEL, MODEL_OUTPUT)
        }

        private fun createProcessMethodReference(params: String, definingClass: String, returnType: String): MethodReference {
            return ImmutableMethod(
                definingClass,
                "process",
                ParamUtil.parseParamString(params),
                returnType,
                0,
                null,
                null
            )
        }

        private fun createOutputGetterMethodReference(
            name: String,
            returnType: String
        ): MethodReference {
            return createOutputGetterMethodReference(MODEL_OUTPUT, name, returnType)
        }

        private fun createOutputGetterMethodReference(
            definingClass: String,
            name: String,
            returnType: String
        ): MethodReference {
            return ImmutableMethod(
                definingClass,
                name,
                ParamUtil.parseParamString(""),
                returnType,
                0,
                null,
                null
            )
        }
    }
}