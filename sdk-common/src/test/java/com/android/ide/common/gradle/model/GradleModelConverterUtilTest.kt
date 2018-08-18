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

package com.android.ide.common.gradle.model

import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [GradleModelConverterUtil].
 */
class GradleModelConverterUtilTest {
    @Test
    fun testClassFieldsToDynamicResourceValues() {
        val input = mapOf(
            "foo" to ClassFieldStub(ResourceType.STRING.getName(), "foo", "baz"),
            "foo2" to ClassFieldStub(ResourceType.INTEGER.getName(), "foo2", "123")
        )
        val output = classFieldsToDynamicResourceValues(input)

        val expectedOutput = mapOf(
            "foo" to DynamicResourceValue(ResourceType.STRING, "baz"),
            "foo2" to DynamicResourceValue(ResourceType.INTEGER, "123")
        )

        assertThat(output).isEqualTo(expectedOutput)
    }

    @Test
    fun testConvertAndroidLibrary() {
        val original = com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStub()
        val result = convertLibrary(original)

        with(original) {
            assertThat(result).isEqualTo(
              ExternalLibrary(
                    address = artifactAddress,
                    location = artifact.toPathString(),
                    manifestFile = PathString(manifest),
                    classesJar = PathString(jarFile),
                    dependencyJars = localJars.map(::PathString),
                    resFolder = PathString(resFolder),
                    symbolFile = PathString(symbolFile),
                    resApkFile = resStaticLibrary?.let(::PathString)
                )
            )
        }
    }
}