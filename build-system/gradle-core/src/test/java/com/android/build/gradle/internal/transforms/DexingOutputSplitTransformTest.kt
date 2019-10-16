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

import com.android.build.gradle.internal.dependency.DEX_DIR_NAME
import com.android.build.gradle.internal.dependency.DexingOutputSplitTransform
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeTransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DexingOutputSplitTransformTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testInputWithoutKeepRulesFile() {
        val input = tmp.newFolder()
        input.resolve(DEX_DIR_NAME).mkdir()

        val transform = TestDexingOutputSplitTransform(
            FakeGradleProvider(FakeGradleDirectory(input)),
            parameters = TestDexingOutputSplitTransform.TestParameters(false)
        )

        val output = FakeTransformOutputs(tmp)
        transform.transform(output)
        assert(output.outputFile.isFile)
    }
}

private class TestDexingOutputSplitTransform(
    override val primaryInput: Provider<FileSystemLocation>,
    private val parameters: TestParameters
) : DexingOutputSplitTransform() {

    class TestParameters(
      toDex: Boolean
    ) : DexingOutputSplitTransform.Parameters {
        override var projectName = FakeGradleProperty(":test")
        override var toDex = FakeGradleProperty(toDex)
    }

    override fun getParameters(): Parameters {
        return parameters
    }
}

