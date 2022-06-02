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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class ByLazyDetectorTest {
    @Test
    fun testProblems() {
        studioLint()
            .files(
                kotlin(
                    """
                    @file:Suppress("unused")
                    package test.pkg
                    private class GradleVersion
                    private class GradleCoordinate
                    private class TypeRewriter
                    private val dependencyInfo by lazy { HashMap<GradleVersion, List<GradleCoordinate>>() } // ERROR 1

                    private class TransportId(private val value: Long) {
                        val hostPrefix: String by lazy { // ERROR 2
                            "host-transport-id:＄value"
                        }
                    }
                    private val typeRewriter by lazy(LazyThreadSafetyMode.NONE) { // OK
                        TypeRewriter()
                    }

                    val component: List<String> by lazy {
                        MutableList(5) { "" }.apply { // OK
                            clear()
                        }
                    }

                    private val properties by lazy { getInstance() } // ERROR 3
                    private fun getInstance(): String = "instance"
                """
                ).indented()
            )
            .issues(ByLazyDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/GradleVersion.kt:6: Error: Avoid by lazy for simple lazy initialization [AvoidByLazy]
                private val dependencyInfo by lazy { HashMap<GradleVersion, List<GradleCoordinate>>() } // ERROR 1
                                              ~~~~
                src/test/pkg/GradleVersion.kt:9: Error: Avoid by lazy for simple lazy initialization [AvoidByLazy]
                    val hostPrefix: String by lazy { // ERROR 2
                                              ~~~~
                src/test/pkg/GradleVersion.kt:23: Error: Avoid by lazy for simple lazy initialization [AvoidByLazy]
                private val properties by lazy { getInstance() } // ERROR 3
                                          ~~~~
                3 errors, 0 warnings
                """
            )
    }
}
