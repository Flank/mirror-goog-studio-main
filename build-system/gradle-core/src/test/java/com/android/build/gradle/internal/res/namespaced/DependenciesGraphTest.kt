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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DependenciesGraphTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyGraph() {
        val result = DependenciesGraph.create(ImmutableSet.of())

        assertThat(result.rootNodes).isEmpty()
        assertThat(result.allNodes).isEmpty()
    }

    @Test
    fun noArtifactsTest() {
        //        a    b
        //      /  \ /  \
        //    c    d    e
        //  /  \ /
        // f    g
        val g = createDependency("g")
        val f = createDependency("f")
        val e = createDependency("e")
        val d = createDependency("d", ImmutableSet.of(g))
        val c = createDependency("c", ImmutableSet.of(f, g))
        val b = createDependency("b", ImmutableSet.of(d, e))
        val a = createDependency("a", ImmutableSet.of(c, d))

        val result = DependenciesGraph.create(ImmutableSet.of(a, b))

        assertThat(result.rootNodes).hasSize(2)
        assertThat(result.allNodes).hasSize(7)
        assertThat(visit(result.rootNodes)).containsAllIn(listOf("a", "b", "c", "d", "e", "f", "g"))
    }

    @Test
    fun graphWithArtifacts() {
        //     a
        //    / \
        //   b  c
        //       \
        //        d
        val d = createDependency("d")
        val c = createDependency("c", ImmutableSet.of(d))
        val b = createDependency("b")
        val a = createDependency("a", ImmutableSet.of(b, c))

        val fileA = temporaryFolder.newFile("a.txt")
        val fileB = temporaryFolder.newFile("b.txt")
        val fileC = temporaryFolder.newFile("c.txt")
        val fileD = temporaryFolder.newFile("d.txt")

        val artifacts: ImmutableMap<String, File> =
                ImmutableMap.of("a", fileA, "b", fileB, "c", fileC, "d", fileD)

        val result = DependenciesGraph.create(
                ImmutableSet.of(a),
                artifacts = ImmutableMap.of(AndroidArtifacts.ArtifactType.CLASSES, artifacts)
        )

        assertThat(result.rootNodes).hasSize(1)
        assertThat(result.allNodes).hasSize(4)
        val root = result.rootNodes.first()
        assertThat(root.getTransitiveFiles(AndroidArtifacts.ArtifactType.CLASSES))
            .containsExactlyElementsIn(listOf(fileA, fileB, fileC, fileD))
    }

    private fun visit(nodes: ImmutableSet<DependenciesGraph.Node>): List<String> {
        val names = ImmutableList.Builder<String>()
        nodes.forEach { visit(it, names) }
        return names.build()
    }

    private fun visit(node: DependenciesGraph.Node, names: ImmutableList.Builder<String>) {
        for (child in node.dependencies) {
            visit(child, names)
        }
        names.add(node.id.displayName)
    }
}
