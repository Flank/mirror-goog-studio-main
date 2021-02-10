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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject.Companion.lib
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestUtils
import com.android.testutils.truth.ZipFileSubject
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.function.Consumer

/** Tests for ML Model Binding feature in an Android library. */
class MlModelBindingInLibTest {
    @JvmField
    @Rule
    val project: GradleTestProject = builder()
        .fromTestApp(lib("com.example.lib"))
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
            """
        )
        TestFileUtils.appendToFile(project.gradlePropertiesFile, "android.useAndroidX=true")
    }

    @Test
    fun testModelClassGenerated() {
        FileUtils.copyFile(
            TestUtils.resolveWorkspacePath(
                "prebuilts/tools/common/mlkit/testData/models/mobilenet_quant_metadata.tflite"
            ).toFile(),
            project.file("src/main/ml/model.tflite")
        )

        project.executor().run(":assembleDebug")
        project.getAar("debug", Consumer { aar ->
            val classesJar = aar.getEntryAsZip("classes.jar")
            ZipFileSubject.assertThat(classesJar.file) { it.contains("/com/example/lib/ml/Model.class") }
        })
    }
}
