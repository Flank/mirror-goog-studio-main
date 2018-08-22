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
}
