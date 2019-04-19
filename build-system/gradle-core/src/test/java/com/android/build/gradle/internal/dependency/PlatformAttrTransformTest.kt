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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.fixtures.FakeGenericTransformParameters
import com.android.testutils.TestResources
import com.google.common.io.Files
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.artifacts.transform.TransformOutputs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.fail

class PlatformAttrTransformTest {
    @get:Rule
    var outputDir = TemporaryFolder()

    @Test
    fun testExtraction() {
        val transform = object : PlatformAttrTransform() {
            override val primaryInput: File
                get() = TestResources.getFile(
                    PlatformAttrTransformTest::class.java, "PlatformAttrTransform.jar")

            override fun getParameters()= FakeGenericTransformParameters()
        }

        var outputFile: File? = null
        val outputs = object: TransformOutputs {
            override fun file(p0: Any): File {
                if (outputFile != null) fail("unexpected multiple calls to 'file'")
                outputFile = outputDir.newFile(p0.toString())
                return outputFile!!
            }
            override fun dir(p0: Any): File {
                fail("unexpected 'dir' method call")
            }
        }

        transform.transform(outputs)

        assertThat(outputFile).isNotNull()
        val lines = Files.readLines(outputFile!!, Charsets.UTF_8)

        Truth.assertThat(lines).containsExactly("int attr one 0x00000001", "int attr two 0x00000002")
    }
}