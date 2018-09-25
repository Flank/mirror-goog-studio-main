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

package com.android.projectmodel

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [ConfigTableSchema].
 */
class ConfigTableSchemaTest {
    val schema = configTableSchemaWith(
        "paymentModel" to listOf("paid", "free"),
        "resolution" to listOf("hires", "lowres"),
        "buildType" to listOf("debug", "release")
    )

    @Test
    fun testToString() {
        assertThat(schema.toString()).isEqualTo("ConfigTableSchema(paymentModel[paid,free],resolution[hires,lowres],buildType[debug,release],artifact[_main_,_unit_test_,_android_test_])")
    }

    @Test
    fun testDefaultToString() {
        assertThat(ConfigTableSchema().toString()).isEqualTo("ConfigTableSchema(artifact[_main_,_unit_test_,_android_test_])")
    }

    @Test
    fun testPathFor() {
        assertThat(schema.pathFor(null)).isEqualTo(matchAllArtifacts())
        assertThat(schema.pathFor("paid")).isEqualTo(matchArtifactsWith("paid"))
        assertThat(schema.pathFor("lowres")).isEqualTo(matchArtifactsWith("*/lowres"))
        assertThat(schema.pathFor("release")).isEqualTo(matchArtifactsWith("*/*/release"))
        assertThat(schema.pathFor(ARTIFACT_NAME_MAIN)).isEqualTo(matchArtifactsWith("*/*/*/${ARTIFACT_NAME_MAIN}"))
        assertThat(schema.pathFor("")).isEqualTo(matchNoArtifacts())
    }

    @Test
    fun testContainsPath() {
        val testSubmodulePaths = listOf(
            submodulePathForString("") to true,
            submodulePathForString("paid") to true,
            submodulePathForString("hires") to false,
            submodulePathForString("blorg") to false,
            submodulePathForString("paid/hires/debug") to true,
            submodulePathForString("paid/hires/debug/boom") to false,
            submodulePathForString("free/hires/release") to true,
            submodulePathForString("free/lowres") to true
        )

        for (next in testSubmodulePaths) {
            Truth.assertWithMessage("Testing schema.isValid(${next.first})")
                .that(schema.isValid(next.first)).isEqualTo(next.second)
            Truth.assertWithMessage("Testing schema.isValid(${next.first.toConfigPath()})")
                .that(schema.isValid(next.first.toConfigPath())).isEqualTo(next.second)
        }
    }

    @Test
    fun testContainsPathWithWildcard() {
        val testConfigPaths = listOf(
            matchAllArtifacts() to true,
            matchNoArtifacts() to false,
            matchArtifactsWith("*") to true,
            matchArtifactsWith("paid/*/debug") to true,
            matchArtifactsWith("paid/*/debug/*") to true,
            matchArtifactsWith("paid/*/debug/boom") to false,
            matchArtifactsWith("*/*/release") to true
        )

        for (next in testConfigPaths) {
            Truth.assertThat(schema.isValid(next.first)).isEqualTo(next.second)
        }
    }

    @Test
    fun testAllPaths() {
        assertThat(schema.allPaths().toList()).containsExactly(
            matchArtifactsWith("paid/hires/debug/_main_"),
            matchArtifactsWith("paid/hires/debug/_unit_test_"),
            matchArtifactsWith("paid/hires/debug/_android_test_"),
            matchArtifactsWith("paid/hires/release/_main_"),
            matchArtifactsWith("paid/hires/release/_unit_test_"),
            matchArtifactsWith("paid/hires/release/_android_test_"),
            matchArtifactsWith("paid/lowres/debug/_main_"),
            matchArtifactsWith("paid/lowres/debug/_unit_test_"),
            matchArtifactsWith("paid/lowres/debug/_android_test_"),
            matchArtifactsWith("paid/lowres/release/_main_"),
            matchArtifactsWith("paid/lowres/release/_unit_test_"),
            matchArtifactsWith("paid/lowres/release/_android_test_"),
            matchArtifactsWith("free/hires/debug/_main_"),
            matchArtifactsWith("free/hires/debug/_unit_test_"),
            matchArtifactsWith("free/hires/debug/_android_test_"),
            matchArtifactsWith("free/hires/release/_main_"),
            matchArtifactsWith("free/hires/release/_unit_test_"),
            matchArtifactsWith("free/hires/release/_android_test_"),
            matchArtifactsWith("free/lowres/debug/_main_"),
            matchArtifactsWith("free/lowres/debug/_unit_test_"),
            matchArtifactsWith("free/lowres/debug/_android_test_"),
            matchArtifactsWith("free/lowres/release/_main_"),
            matchArtifactsWith("free/lowres/release/_unit_test_"),
            matchArtifactsWith("free/lowres/release/_android_test_")
        )
    }

    @Test
    fun testAllPathsOfLength() {
        assertThat(schema.allPathsOfLength(2).toList()).containsExactly(
            matchArtifactsWith("paid/hires"),
            matchArtifactsWith("paid/lowres"),
            matchArtifactsWith("free/hires"),
            matchArtifactsWith("free/lowres")
        )
    }
}
