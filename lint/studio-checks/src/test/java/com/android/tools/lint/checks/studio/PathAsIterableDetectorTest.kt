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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import org.junit.Test

class PathAsIterableDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                kotlin(
                    """
                    package test.pkg

                    import java.io.File
                    import java.nio.file.Path

                    fun test(root: File): List<Path> {
                        // This will actually create a list of all path elements instead of a list with
                        // a single path element
                        return newArrayList(File(root, "something").toPath())
                    }

                    fun <E> newArrayList(elements: Iterable<E>): ArrayList<E> = TODO()
                """
                ).indented()
            )
            .issues(PathAsIterableDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/test.kt:9: Error: Using Path in an Iterable context: make sure this is doing what you expect and suppress this warning if so [PathAsIterable]
                    return newArrayList(File(root, "something").toPath())
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
            )
    }
}
