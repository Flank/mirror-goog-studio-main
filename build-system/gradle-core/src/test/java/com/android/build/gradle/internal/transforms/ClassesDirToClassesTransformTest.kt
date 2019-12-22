/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import com.android.build.gradle.internal.dependency.ClassesDirToClassesTransform
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClassesDirToClassesTransformTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `test inputDir contains class files only`() {
        val inputDir = tmp.newFolder().also {
            it.resolve("A.class").createNewFile()
        }

        val output = FakeTransformOutputs(tmp)
        val transform = TestClassesDirToClassesTransform(
            FakeGradleProvider(FakeGradleDirectory(inputDir))
        )
        transform.transform(output)

        assertThat(output.outputDirectory.path).isEqualTo(inputDir.path)
    }

    @Test
    fun `test inputDir contains a single jar only`() {
        val inputDir = tmp.newFolder().also {
            it.resolve("A.jar").createNewFile()
        }

        val output = FakeTransformOutputs(tmp)
        val transform = TestClassesDirToClassesTransform(
            FakeGradleProvider(FakeGradleDirectory(inputDir))
        )
        transform.transform(output)

        assertThat(output.outputFile.path).isEqualTo(inputDir.resolve("A.jar").path)
    }
}

private class TestClassesDirToClassesTransform(
    override val inputArtifact: Provider<FileSystemLocation>
) : ClassesDirToClassesTransform() {

    override fun getParameters(): GenericTransformParameters {
        return object : GenericTransformParameters {
            override val projectName: Property<String> = FakeGradleProperty("projectName")
        }
    }
}
