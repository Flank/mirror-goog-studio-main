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

import com.android.build.gradle.internal.fixtures.FakeModuleVersionIdentifier
import com.android.build.gradle.internal.fixtures.FakeResolvedComponentResult
import com.android.build.gradle.internal.fixtures.FakeResolvedDependencyResult
import com.google.common.truth.Truth.assertThat
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.junit.Test

class PathAwareDependencyGraphVisitorTest {

    @Test
    fun `test visitOnlyOnce = false`() {
        doTest(false)
    }

    @Test
    fun `test visitOnlyOnce = true`() {
        doTest(true)
    }

    private fun doTest(visitOnlyOnce: Boolean) {
        // Create the following dependency graph:
        //     root
        //     |- a
        //     |  |- b
        //     |  |- c
        //     |- c
        val root = createDependency("root", "root", "1")
        val a = createDependency("a", "a", "1")
        val b = createDependency("b", "b", "1")
        val c = createDependency("c", "c", "1")
        addDependency(root, a)
        addDependency(root, c)
        addDependency(a, b)
        addDependency(a, c)

        // Print paths to dependencies
        val dependencyPaths = mutableListOf<String>()
        object : PathAwareDependencyGraphVisitor(visitOnlyOnce) {
            override fun visitDependency(dependency: ResolvedComponentResult, parentPath: List<ResolvedComponentResult>) {
                dependencyPaths.add((parentPath + dependency).joinToString(" -> ") { it.moduleVersion!!.name })
            }
        }.visitDependencyRecursively(root, listOf())

        if (visitOnlyOnce) {
            assertThat(dependencyPaths).containsExactlyElementsIn(
                    listOf("root", "root -> a", "root -> a -> b", "root -> a -> c")
            )
        } else {
            assertThat(dependencyPaths).containsExactlyElementsIn(
                    listOf("root", "root -> a", "root -> a -> b", "root -> a -> c", "root -> c")
            )
        }
    }

    @Suppress("SameParameterValue")
    private fun createDependency(group: String, name: String, version: String) =
            FakeResolvedComponentResult(
                    moduleVersion = FakeModuleVersionIdentifier(group = group, name = name, version = version),
                    dependencies = mutableSetOf()
            )

    @Suppress("UNCHECKED_CAST")
    private fun addDependency(a: ResolvedComponentResult, b: ResolvedComponentResult) =
            (a.dependencies as MutableSet<DependencyResult>).add(FakeResolvedDependencyResult(selected = b))
}