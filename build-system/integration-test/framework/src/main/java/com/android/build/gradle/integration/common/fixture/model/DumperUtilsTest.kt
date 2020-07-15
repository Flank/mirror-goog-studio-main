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

package com.android.build.gradle.integration.common.fixture.model

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.ide.ApiVersion
import com.google.common.truth.Truth
import com.google.gson.JsonElement
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File

class DumperUtilsTest {

    @get:Rule
    val thrown: ExpectedException = ExpectedException.none()

    @Test
    fun `test basic object`() {
        val apiVersion = FakeApiVersion(12, null)

        val dump = dump(FakeApiVersion::class.java,
            FakeFileNormalizer()
        ) {
            item("apiLevel", apiVersion.apiLevel)
            item("codename", apiVersion.codename)
        }

        Truth.assertThat(dump).isEqualTo("""
            > FakeApiVersion:
                - apiLevel = 12
                - codename = (null)
            < FakeApiVersion

        """.trimIndent())
    }

    @Test
    fun `test property verifier`() {
        val apiVersion = FakeApiVersion(12, null)

        thrown.expectMessage("""
            Properties for interface com.android.builder.model.v2.ide.ApiVersion
            missing (1): codename
        """.trimIndent())

        dump(FakeApiVersion::class.java,
            FakeFileNormalizer()
        ) {
            item("apiLevel", apiVersion.apiLevel)
        }
    }

    @Test
    fun `test properties gathering`() {
        Truth.assertThat(FakeBuildType::class.java.gatherProperties()).containsExactly(
            "name", "isDebuggable", "isTestCoverageEnabled", "isPseudoLocalesEnabled",
            "isJniDebuggable", "isRenderscriptDebuggable", "renderscriptOptimLevel",
            "isMinifyEnabled", "isZipAlignEnabled", "isEmbedMicroApp", "signingConfig",
            "applicationIdSuffix", "versionNameSuffix", "buildConfigFields",
            "resValues", "proguardFiles", "consumerProguardFiles", "testProguardFiles",
            "manifestPlaceholders", "multiDexEnabled", "multiDexKeepFile", "multiDexKeepProguard"
        )
    }
}

// impl of ApiVersion for testing
data class FakeApiVersion(override val apiLevel: Int, override val codename: String?): ApiVersion

class FakeFileNormalizer: FileNormalizer {
    override fun normalize(file: File): String = file.absolutePath
    override fun normalize(value: JsonElement): JsonElement = value
}

// impl of BuildType for testing
data class FakeBuildType(
    override val isDebuggable: Boolean,
    override val isTestCoverageEnabled: Boolean,
    override val isPseudoLocalesEnabled: Boolean,
    override val isJniDebuggable: Boolean,
    override val isRenderscriptDebuggable: Boolean,
    override val renderscriptOptimLevel: Int,
    override val isMinifyEnabled: Boolean,
    override val isZipAlignEnabled: Boolean,
    override val isEmbedMicroApp: Boolean,
    override val signingConfig: String?,
    override val name: String?,
    override val applicationIdSuffix: String?,
    override val versionNameSuffix: String?,
    override val buildConfigFields: Map<String, ClassField>?,
    override val resValues: Map<String, ClassField>?,
    override val proguardFiles: Collection<File>,
    override val consumerProguardFiles: Collection<File>,
    override val testProguardFiles: Collection<File>,
    override val manifestPlaceholders: Map<String, Any>,
    override val multiDexEnabled: Boolean?,
    override val multiDexKeepFile: File?,
    override val multiDexKeepProguard: File?
): BuildType