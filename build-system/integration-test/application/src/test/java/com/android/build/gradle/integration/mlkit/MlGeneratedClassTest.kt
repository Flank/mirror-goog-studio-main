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
                    implementation 'org.tensorflow:tensorflow-lite-support:0.1.0'
                    implementation 'org.tensorflow:tensorflow-lite-metadata:0.1.0'
                }

                android {
                    compileOptions {
                        sourceCompatibility 1.8
                        targetCompatibility 1.8
                    }
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
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                ),
                createOutputGetterMethodReference(
                    "getProbabilityAsTensorBuffer", TENSOR_BUFFER
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
                createProcessMethodReference(TENSOR_IMAGE, MODEL_SUB, MODEL_OUTPUT_SUB),
                createProcessMethodReference(TENSOR_BUFFER, MODEL_SUB, MODEL_OUTPUT_SUB),
                createCloseMethodReference(MODEL_SUB)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createGetterMethodReference(
                    MODEL_OUTPUT_SUB,
                    "getProbabilityAsCategoryList", LIST
                ),
                createGetterMethodReference(
                    MODEL_OUTPUT_SUB,
                    "getProbabilityAsTensorBuffer", TENSOR_BUFFER
                )
            )
        verifyModelClass(
            "mobilenet_quant_metadata.tflite",
            modelMethods,
            outputsMethods,
            null,
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
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
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
    fun testMobileNetModelClassWithV2Metadata() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsTensorBuffer", TENSOR_BUFFER
                )
            )
        verifyModelClass("mobilenet_quant_metadata_v2.tflite", modelMethods, outputsMethods)
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testDeepLabModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
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
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                ),
                createOutputGetterMethodReference(
                    "getProbabilityAsTensorBuffer", TENSOR_BUFFER
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
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getProbabilityAsCategoryList", LIST
                ),
                createOutputGetterMethodReference(
                    "getProbabilityAsTensorBuffer", TENSOR_BUFFER
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
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
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
    fun testSsdOdtModelClass_v2() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE),
                createProcessMethodReference(TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
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
                ),
                createOutputGetterMethodReference(
                    "getDetectionResultList", LIST
                )
            )
        val groupClassMethods: Set<MethodReference> =
            ImmutableSet.of(
                createGetterMethodReference(
                    MODEL_DETECTION_RESULT, "getClassesAsString", STRING
                ),
                createGetterMethodReference(
                    MODEL_DETECTION_RESULT, "getLocationsAsRectF", RECT_F
                ),
                createGetterMethodReference(
                    MODEL_DETECTION_RESULT, "getScoresAsFloat", "F"
                )
            )
        verifyModelClass(
            "ssd_mobilenet_odt_metadata_v1.2.tflite",
            modelMethods,
            outputsMethods,
            GroupClassInfo(MODEL_DETECTION_RESULT, groupClassMethods)
        )
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testStyleTransferModelClass() {
        val modelMethods: Set<MethodReference> =
            ImmutableSet.of(
                createNewInstanceMethodReference(),
                createNewInstanceWithOptionsMethodReference(MODEL),
                createProcessMethodReference(TENSOR_IMAGE + TENSOR_BUFFER),
                createProcessMethodReference(TENSOR_BUFFER + TENSOR_BUFFER),
                createCloseMethodReference(MODEL)
            )
        val outputsMethods: Set<MethodReference> =
            ImmutableSet.of(
                createOutputGetterMethodReference(
                    "getStyledImageAsTensorImage", TENSOR_IMAGE
                ),
                createOutputGetterMethodReference(
                    "getStyledImageAsTensorBuffer", TENSOR_BUFFER
                )
            )
        verifyModelClass("style_transfer_quant_metadata.tflite", modelMethods, outputsMethods)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun verifyModelClass(
        modelFileName: String,
        expectedModelMethods: Set<MethodReference>,
        expectedOutputMethods: Set<MethodReference>,
        groupClassInfo: GroupClassInfo? = null,
        verifySubFolder: Boolean = false
    ) {
        val dstModelFilePath = if(verifySubFolder) "src/main/ml/sub/mobilenet_model.tflite" else "src/main/ml/model.tflite"
        val modelClass = if(verifySubFolder) MODEL_SUB else MODEL
        val modelOutputClass = if(verifySubFolder) MODEL_OUTPUT_SUB else MODEL_OUTPUT
        val apkModelFile = if(verifySubFolder) "assets/sub/mobilenet_model.tflite" else "assets/model.tflite"

        // Add model file to ml folder
        val modelFile = project.file(dstModelFilePath)
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/$modelFileName"
            ).toFile(),
            modelFile
        )
        project.executor().run(":assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk.getClass(modelClass)!!.methods).containsAtLeastElementsIn(expectedModelMethods)
        assertThat(apk.getClass(modelOutputClass)!!.methods).containsAtLeastElementsIn(
            expectedOutputMethods
        )

        if (groupClassInfo != null) {
            assertThat(apk.getClass(groupClassInfo.className)!!.methods).containsAtLeastElementsIn(
                groupClassInfo.methods
            )
        }

        // Check model.tflite is uncompressed (Issue 152875817)
        val entry = ZipArchive.listEntries(apk.file)[apkModelFile]
        assertThat(entry?.compressionFlag).isEqualTo(ZipEntry.STORED)
    }

    companion object {
        private const val MODEL = "Lcom/android/app/ml/Model;"
        private const val MODEL_SUB = "Lcom/android/app/ml/MobilenetModel219;"
        private const val MODEL_OUTPUT = "Lcom/android/app/ml/Model\$Outputs;"
        private const val MODEL_DETECTION_RESULT = "Lcom/android/app/ml/Model\$DetectionResult;"
        private const val MODEL_OUTPUT_SUB = "Lcom/android/app/ml/MobilenetModel219\$Outputs;"
        private const val TENSOR_IMAGE = "Lorg/tensorflow/lite/support/image/TensorImage;"
        private const val TENSOR_BUFFER =
            "Lorg/tensorflow/lite/support/tensorbuffer/TensorBuffer;"
        private const val LIST = "Ljava/util/List;"
        private const val CONTEXT = "Landroid/content/Context;"
        private const val MODEL_OPTIONS = "Lorg/tensorflow/lite/support/model/Model\$Options;"
        private const val STRING = "Ljava/lang/String;"
        private const val RECT_F = "Landroid/graphics/RectF;"

        data class GroupClassInfo(val className:String, val methods: Set<MethodReference>)

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

        private fun createCloseMethodReference(definingClass: String): MethodReference {
            return ImmutableMethod(
                definingClass,
                "close",
                null,
                "V",
                0,
                null,
                null
            )
        }

        private fun createOutputGetterMethodReference(
            name: String,
            returnType: String
        ): MethodReference {
            return createGetterMethodReference(MODEL_OUTPUT, name, returnType)
        }

        private fun createGetterMethodReference(
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
