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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class BuildSettingsFactoryKtTest {
    private val exampleJsonString = """
        {
            "environmentVariables": [
                {
                    "name": "USE_CACHE",
                    "value": "false"
                },
                {
                    "name": "USE_LOCAL",
                    "value": "true"
                }
            ]
        }
        """.trimIndent()

    @Test
    fun `parse basic json string`() {
        val setting = createBuildSettingsFromJson(exampleJsonString)
        assertThat(setting.environmentVariables).isEqualTo(
            listOf(
                EnvironmentVariable("USE_CACHE", "false"),
                EnvironmentVariable("USE_LOCAL", "true")
            )
        )
    }

    @Test
    fun `invalid json shows error and returns empty model`() {
        val invalidJson = ""

        PassThroughDeduplicatingLoggingEnvironment().apply {
            val model = createBuildSettingsFromJson(invalidJson)

            assertThat(errors.single()).isEqualTo("C/C++: Json is empty")
            assertThat(model).isEqualTo(BuildSettingsConfiguration())
            assertThat(model.environmentVariables?.size).isEqualTo(0)
        }
    }

    @Test
    fun `missing BuildSettings file returns empty model`() {
        val model = createBuildSettingsFromFile(File("invalid/path"))
        assertThat(model).isEqualTo(BuildSettingsConfiguration())
    }

    @Test
    fun `returns environment variable map`() {
        val setting = createBuildSettingsFromJson(exampleJsonString)
        assertThat(setting.getEnvironmentVariableMap()).isEqualTo(
            mapOf("USE_CACHE" to "false", "USE_LOCAL" to "true")
        )
    }

    @Test
    fun `environment variables with no name is not included`() {
        val invalidString = """
        {
            "environmentVariables": [
                { "value": "false" },
                { "name": "USE_LOCAL", "value": "true" }
            ]
        }
        """.trimIndent()

        val setting = createBuildSettingsFromJson(invalidString)
        assertThat(setting.getEnvironmentVariableMap()).isEqualTo(
            mapOf("USE_LOCAL" to "true")
        )
    }
}
