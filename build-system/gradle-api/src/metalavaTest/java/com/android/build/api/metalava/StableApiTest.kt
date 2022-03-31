/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.metalava

import com.android.testutils.TestUtils
import com.google.common.io.Resources
import org.junit.Test
import java.lang.AssertionError
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test that tries to ensure that our public API remains stable.
 */
class StableApiTest {
    @Test
    fun checkCurrentApi() {
        @Suppress("UnstableApiUsage")
        val expected: List<String> = Resources.asCharSource(EXPECTED_FILE, StandardCharsets.UTF_8).readLines()
        val actual = Files.readAllLines(CURRENT, StandardCharsets.UTF_8)
        if (expected != actual) {
            throw AssertionError(
                """
                    The Android Gradle Plugin API does not match the expectation file.

                    Either:
                      * revert the api change
                      * or apply the below changes by running the updateApi task:
                            gradle :base:gradle-api:updateApi

                    """.trimIndent() +
                        TestUtils.getDiff(expected.toTypedArray(), actual.toTypedArray())
            )
        }
    }

    companion object {
        @Suppress("UnstableApiUsage")
        private val EXPECTED_FILE = Resources.getResource("current.txt")
        val CURRENT: Path = Paths.get(System.getProperty("metalavaCurrentApiFile")!!).resolve("current.txt")
    }
}
