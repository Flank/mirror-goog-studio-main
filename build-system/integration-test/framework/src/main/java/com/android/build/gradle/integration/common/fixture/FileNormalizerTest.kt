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

package com.android.build.gradle.integration.common.fixture

import com.google.common.truth.Truth
import org.gradle.tooling.model.BuildIdentifier
import org.junit.Test
import java.io.File

class FileNormalizerTest {

    private val normalizer = FileNormalizerImpl(
        buildId = BuildIdentifierImpl(File("/path/to/Project")),
        gradleUserHome = File("/path/to/Gradle"),
        androidSdk = File("/path/to/Sdk"),
        androidHome = File("/path/to/Home")
    )

    @Test
    fun `Test Outside Paths`() {
        Truth.assertThat(normalizer.normalize(File("/wrong/path")))
            .isEqualTo(File("/wrong/path"))
    }

    @Test
    fun `Test Project Paths`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Project/foo")))
            .isEqualTo(File("{PROJECT}/foo"))

        Truth.assertThat(normalizer.normalize(File("/wrong/path")))
            .isEqualTo(File("/wrong/path"))
    }

    @Test
    fun `Test Gradle User Home`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Gradle/foo")))
            .isEqualTo(File("{GRADLE}/foo"))
    }

    @Test
    fun `Test Gradle Transform Cache`() {
        Truth.assertThat(normalizer.normalize(
            File("/path/to/Gradle/caches/transforms-2/files-2.1/12345678901234567890123456789012/foo")
        ))
            .isEqualTo(File("{GRADLE}/caches/transforms-2/{CHECKSUM}/foo"))
    }

    @Test
    fun `Test android Sdk`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Sdk/foo")))
            .isEqualTo(File("{SDK}/foo"))
    }

    @Test
    fun `Test android Home`() {
        Truth.assertThat(normalizer.normalize(File("/path/to/Home/foo")))
            .isEqualTo(File("{ANDROID_HOME}/foo"))
    }

    private class BuildIdentifierImpl(private val rootDir: File): BuildIdentifier {
        override fun getRootDir(): File {
            return rootDir
        }
    }
}