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
 * Test cases for [ArtifactDependency]
 */
class ArtifactDependencyTest {
    // Two dependency instances with the same content
    private val equalDep1 = ArtifactDependency(library=ProjectLibrary("dep", "dep", "main"))
    private val equalDep2 = ArtifactDependency(library=ProjectLibrary("dep", "dep", "main"))
    // A dependency instance that will show up multiple times in the dependency graph
    private val uniqueDep = ArtifactDependency(library=ProjectLibrary("uniqueDep", "uniqueDep", "main"))
    private val recursiveDep1 = ArtifactDependency(
        library=ProjectLibrary("recursiveDep1", "recursiveDep1", "main"),
        dependencies = listOf(equalDep1, uniqueDep)
    )
    private val recursiveDep2 = ArtifactDependency(
        library=ProjectLibrary("recursiveDep2", "recursiveDep2", "main"),
        dependencies = listOf(equalDep2, uniqueDep)
    )
    private val deps = listOf(
        recursiveDep1, recursiveDep2, uniqueDep
    )
    /**
     * Tests the [visitEach] method.
     */
    @Test
    fun testVisitEach() {
        assertThat(deps.visitEach().toList()).containsExactly(
            equalDep1,
            uniqueDep,
            recursiveDep1,
            equalDep2,
            recursiveDep2
        )
    }
}
