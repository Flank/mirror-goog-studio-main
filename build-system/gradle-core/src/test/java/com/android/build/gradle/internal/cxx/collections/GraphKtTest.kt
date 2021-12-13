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
package com.android.build.gradle.internal.cxx.collections
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GraphKtTest {
    @Test
    fun `check breadthFirst`() {
        assertThat(graphOf(
            1 to setOf(2, 3),
            2 to setOf(3, 4))
            .renderBreadthFirst(1))
            .isEqualTo("""
                1, 2, 3, 4
            """.trimIndent())
        assertThat(graphOf(
            1 to setOf(2, 3),
            2 to setOf(3, 4))
            .renderBreadthFirst(2))
            .isEqualTo("""
                2, 3, 4
            """.trimIndent())
        assertThat(graphOf(
            1 to setOf(2, 3),
            2 to setOf(3, 4))
            .renderBreadthFirst(3))
            .isEqualTo("""
                3
            """.trimIndent())
    }

    @Test
    fun `check ancestors`() {
        assertThat(graphOf()
            .renderAncestorsOf())
            .isEqualTo("""
            """.trimIndent())
        assertThat(graphOf()
            .renderAncestorsOf(5))
            .isEqualTo("""
                5 -> 5
            """.trimIndent())
        assertThat(graphOf(
            0 to setOf(1),
            2 to setOf(0))
            .renderAncestorsOf(2))
            .isEqualTo("""
                2 -> 2
            """.trimIndent())
        assertThat(graphOf(
            1 to setOf(2, 3),
            2 to setOf(3, 4))
            .renderAncestorsOf(3, 4))
            .isEqualTo("""
                3 -> 3
                4 -> 4
                2 -> 3, 4
                1 -> 3, 4
            """.trimIndent())
        // Cycle
        assertThat(graphOf(
            1 to setOf(2),
            2 to setOf(1))
            .renderAncestorsOf(2))
            .isEqualTo("""
                2 -> 2
                1 -> 2
            """.trimIndent())
        assertThat(graphOf(
            1 to setOf(2),
            2 to setOf(3),
            3 to setOf(1))
            .renderAncestorsOf(1))
            .isEqualTo("""
                1 -> 1
                3 -> 1
                2 -> 1
            """.trimIndent())
    }

    private fun Map<Int, IntArray>.renderAncestorsOf(vararg terminals : Int) : String {
        return ancestors(terminals.toSet())
            .toMap()
            .render()
    }

    private fun Map<Int, IntArray>.renderBreadthFirst(initial : Int) : String {
        return breadthFirst(initial).toList().render()
    }

    private fun Map<Int, IntArray>.render() : String {
        return toList().joinToString("\n") { (inEdge, outEdges) ->
            "$inEdge -> ${outEdges.joinToString(", ")}"
        }
    }

    private fun List<Int>.render() : String {
        return joinToString(", ")
    }
}
