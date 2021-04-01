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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.fixtures.FakeResolutionResult
import com.android.build.gradle.internal.fixtures.addDependencyEdge
import com.android.build.gradle.internal.fixtures.createModuleComponent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ResolutionResultUtilsTest {

    lateinit var resolutionResult: FakeResolutionResult

    @Before
    fun setUp() {
        // Create the following dependency graph:
        //     root
        //     |- a
        //     |  |- b
        //     |  |- c
        //     |- c
        val root = createModuleComponent("root", "root", "1")
        val a = createModuleComponent("a", "a", "1")
        val b = createModuleComponent("b", "b", "1")
        val c = createModuleComponent("c", "c", "1")
        addDependencyEdge(root, a)
        addDependencyEdge(root, c)
        addDependencyEdge(a, b)
        addDependencyEdge(a, c)

        resolutionResult = FakeResolutionResult(root)
    }

    @Test
    fun `test getModuleComponents()`() {
        val foundModuleComponents = resolutionResult
                .getModuleComponents { it.module == "c" }
                .map { it.id.displayName }
        assertThat(foundModuleComponents).containsExactly("c:c:1")
    }

    @Test
    fun `test getPathFromRoot()`() {
        val moduleComponent = resolutionResult.getModuleComponents { it.module == "c" }.single()
        val pathFromRoot = moduleComponent.getPathFromRoot().getPathString()
        assertThat(
                pathFromRoot == "root:root:1 -> c:c:1"
                        || pathFromRoot == "root:root:1 -> a:a:1 -> c:c:1"
        ).isTrue()
    }
}
