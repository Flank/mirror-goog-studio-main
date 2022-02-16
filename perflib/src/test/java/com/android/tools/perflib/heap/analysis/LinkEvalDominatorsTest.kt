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
package com.android.tools.perflib.heap.analysis

import junit.framework.Assert.assertEquals
import junit.framework.TestCase
import java.util.stream.Stream

class LinkEvalDominatorsTest: TestCase() {

    fun testSimpleGraph() = with (compute(Graph.Builder<Int>()
                                      .addEdges(1, 2, 3)
                                      .addEdges(2, 4, 6)
                                      .addEdges(3, 4, 5)
                                      .addEdges(4, 6)
                                      .addRoots(1))) {
        assertEquals(6, countReachable())
        assertImmDominates(1, 2)
        assertImmDominates(1, 3)
        assertImmDominates(1, 4)
        assertImmDominates(1, 6)
        assertImmDominates(3, 5)
    }

    fun testCyclicGraph() = with(compute(Graph.Builder<Int>()
                                             .addEdges(1, 2, 3, 4)
                                             .addEdges(2, 3)
                                             .addEdges(3, 4)
                                             .addEdges(4, 2)
                                             .addRoots(1))){
        assertEquals(4, countReachable())
        assertImmDominates(1, 2)
        assertImmDominates(1, 3)
        assertImmDominates(1, 4)
    }

    fun testMultipleRoots() = with (compute(Graph.Builder<Int>()
                                                .addEdges(1, 3)
                                                .addEdges(2, 4)
                                                .addEdges(3, 5)
                                                .addEdges(4, 5)
                                                .addEdges(5, 6)
                                                .addRoots(1, 2))) {
        assertEquals(6, countReachable())
        assertImmDominates(1, 3)
        assertImmDominates(2, 4)
        // Node 5 is reachable via both roots, neither of which can be the sole dominator.
        assertNull(getImmDom(5))
        assertImmDominates(5, 6)
    }

    fun testMultiplePaths() = with(compute(Graph.Builder<Int>()
                                               .addEdges(1, 7, 8)
                                               .addEdges(7, 2, 3)
                                               .addEdges(8, 2)
                                               .addEdges(2, 4)
                                               .addEdges(3, 5)
                                               .addEdges(5, 4)
                                               .addEdges(4, 6)
                                               .addRoots(1))) {
        assertEquals(1, getImmDom(4))
        assertEquals(4, getImmDom(6))
    }

    private inline fun<reified T: Any> compute(g: Graph.Builder<T>) = with(g.build()) {
        LinkEvalDominators.computeDominators(roots, next, prev)
    }
}

class Graph<T>(val roots: Set<T>, val next: (T) -> Stream<T>, val prev: (T) -> Stream<T>) {
    class Builder<T> {
        private val next = mutableMapOf<T, MutableSet<T>>()
        private val prev = mutableMapOf<T, MutableSet<T>>()
        private val roots = mutableSetOf<T>()

        fun addEdges(from: T, vararg to: T) = this.also {
            next.getOrPut(from, ::mutableSetOf).addAll(to)
            to.forEach { prev.getOrPut(it, ::mutableSetOf).add(from) }
        }

        fun addRoots(vararg roots: T) = this.also { this.roots.addAll(roots) }

        fun build() = Graph(roots,
                            { next[it]?.stream() ?: Stream.empty() },
                            { prev[it]?.stream() ?: Stream.empty() })
    }
}

private fun<T> LinkEvalDominators.Result<T>.countReachable() = topoOrder.count { it != null }
private fun<T> LinkEvalDominators.Result<T>.indexOf(node: T) = topoOrder.indexOf(node)
private fun<T> LinkEvalDominators.Result<T>.getImmDom(node: T) = immediateDominator[indexOf(node)]
private fun<T> LinkEvalDominators.Result<T>.assertImmDominates(er: T, ee: T) =
    assertEquals(er, getImmDom(ee))
