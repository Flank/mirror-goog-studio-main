/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.ide.common.attribution

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CheckJetifierResultTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var resultFile: File

    @Before
    fun setUp() {
        resultFile = temporaryFolder.newFile("result.json")
    }

    private fun createSampleResult(): CheckJetifierResult {
        return CheckJetifierResult(
            LinkedHashMap<String, FullDependencyPath>().apply {
                put(
                    "example:A:1.0",
                    FullDependencyPath(
                        "myProject",
                        "myConfiguration",
                        DependencyPath(listOf("example:A:1.0", "example:B:1.0"))
                    )
                )
            }
        )
    }

    @Test
    fun `test serialization`() {
        val result = createSampleResult()
        CheckJetifierResult.save(result, resultFile)

        assertThat(resultFile.readText()).isEqualTo(
            """
            {
              "version": 1.0,
              "dependenciesDependingOnSupportLibs": {
                "example:A:1.0": {
                  "projectPath": "myProject",
                  "configuration": "myConfiguration",
                  "dependencyPath": {
                    "elements": [
                      "example:A:1.0",
                      "example:B:1.0"
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test deserialization`() {
        val result = createSampleResult()
        CheckJetifierResult.save(result, resultFile)

        val deserializedResult = CheckJetifierResult.load(resultFile)
        assertThat(deserializedResult.getDisplayString()).isEqualTo(result.getDisplayString())
    }

    @Test
    fun `test deserialization where an optional field is missing`() {
        val result = createSampleResult()
        CheckJetifierResult.save(result, resultFile)

        resultFile.writeText(resultFile.readText().replace("configuration", "configurationName"))

        val deserializedResult = CheckJetifierResult.load(resultFile)
        assertThat(deserializedResult.getDisplayString())
            .isEqualTo("example:A:1.0 (Project 'myProject', configuration 'Unknown' -> example:A:1.0 -> example:B:1.0)")
    }

    @Test
    fun `test deserialization where a required field is missing`() {
        val result = createSampleResult()
        CheckJetifierResult.save(result, resultFile)

        resultFile.writeText(resultFile.readText().replace("dependencyPath", "newDependencyPath"))

        try {
            CheckJetifierResult.load(resultFile)
            fail("Expected RequiredFieldMissingException")
        } catch (e: RequiredFieldMissingException) {
            assertThat(e.message).isEqualTo("Required field 'dependencyPath' was missing when deserializing object of type 'com.android.ide.common.attribution.DeserializedFullDependencyPath'")
        }
    }
}
