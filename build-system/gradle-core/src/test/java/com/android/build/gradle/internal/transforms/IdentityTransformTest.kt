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

package com.android.build.gradle.internal.transforms

import com.android.build.gradle.internal.dependency.IdentityTransform
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

class IdentityTransformTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `input is a regular file`() {
        val inputFile = tmp.newFile()
        val output = FakeTransformOutputs(tmp)

        val transform = TestIdentityTransform(
            FakeGradleProvider(FakeGradleRegularFile(inputFile))
        )
        transform.transform(output)

        assertThat(output.outputFile).isEqualTo(inputFile)
    }

    @Test
    fun `input is a directory`() {
        val inputDir = tmp.newFolder()
        val output = FakeTransformOutputs(tmp)

        val transform = TestIdentityTransform(
            FakeGradleProvider(FakeGradleRegularFile(inputDir))
        )
        transform.transform(output)

        assertThat(output.outputDirectory).isEqualTo(inputDir)
    }

    @Test
    fun `input does not exist, acceptNonExistentInputFile not set or set to false, expect failure`() {
        val input = tmp.root.resolve("non-existent")
        val output = FakeTransformOutputs(tmp)

        var transform = TestIdentityTransform(
            FakeGradleProvider(FakeGradleRegularFile(input))
        )
        assertFailsWith(IllegalArgumentException::class) { transform.transform(output) }

        transform = TestIdentityTransform(
            FakeGradleProvider(FakeGradleRegularFile(input)),
            acceptNonExistentInputFile = false
        )
        assertFailsWith(IllegalArgumentException::class) { transform.transform(output) }
    }

    @Test
    fun `input does not exist, acceptNonExistentInputFile set to true, expect success`() {
        val input = tmp.root.resolve("non-existent")
        val output = FakeTransformOutputs(tmp)

        val transform = TestIdentityTransform(
            FakeGradleProvider(FakeGradleRegularFile(input)),
            acceptNonExistentInputFile = true
        )
        transform.transform(output)

        assertThat(output.outputDirectory).isNotEqualTo(input)
        assertThat(output.outputDirectory.isDirectory).isTrue()
        assertThat(output.outputDirectory.listFiles()).isEmpty()
    }
}

private class TestIdentityTransform(
    override val inputArtifact: Provider<FileSystemLocation>,
    private val acceptNonExistentInputFile: Boolean? = null
) : IdentityTransform() {

    override fun getParameters(): Parameters {
        return object : Parameters {
            override val acceptNonExistentInputFile: Property<Boolean> =
                FakeGradleProperty(this@TestIdentityTransform.acceptNonExistentInputFile)
            override val projectName: Property<String> = FakeGradleProperty("projectName")
        }
    }
}
