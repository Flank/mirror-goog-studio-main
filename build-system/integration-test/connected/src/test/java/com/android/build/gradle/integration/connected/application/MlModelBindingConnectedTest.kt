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
package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This test builds mlModelBinding project and runs Android test inside to verify it
 * returns valid model output through generated code.
 *
 * @see com.android.build.gradle.internal.tasks.mlkit.codegen.TfliteModelGenerator
 */
class MlModelBindingConnectedTest {
    @Rule
    @JvmField
    val emulator = getEmulator()

    @Rule
    @JvmField
    var project = builder().fromTestProject("mlModelBinding").create()

    @Before
    fun setup() {
        // Add model file to ml folder
        val imageClassificationModelFile = project.file("src/main/ml/mobilenet_quant_metadata.tflite")
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite"
            ).toFile(),
            imageClassificationModelFile
        )

        val stylePredictModelFile = project.file("src/main/ml/style_predict_quant_metadata.tflite")
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/style_predict_quant_metadata.tflite"
            ).toFile(),
            stylePredictModelFile
        )

        val styleTransferModelFile = project.file("src/main/ml/style_transfer_quant_metadata.tflite")
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/style_transfer_quant_metadata.tflite"
            ).toFile(),
            styleTransferModelFile
        )

        val objectDetectionModelFile = project.file("src/main/ml/ssd_mobilenet_odt_metadata_v1.2.tflite")
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/ssd_mobilenet_odt_metadata_v1.2.tflite"
            ).toFile(),
            objectDetectionModelFile
        )

        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    @Throws(Exception::class)
    fun verifyModelInferenceWithValidOutput() {
        project.executor().run("connectedAndroidTest")
        val htmlResults =
            project.file("build/reports/androidTests/connected/com.example.app.ModelTest.html")
        assertThat(htmlResults).isFile()
    }
}
