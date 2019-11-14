/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.builder.dexing

import org.junit.Test

/** Tests for [MutableDependencyGraph]. */
class MutableDependencyGraphTest {

    @Test
    fun `add edges`() {
        val graph = MutableDependencyGraph<String>()
        assert(graph.getNodes().isEmpty())

        graph.addEdge("1", "2")
        assert(graph.getNodes() == setOf("1", "2"))
        assert(graph.getDirectDependents("1").isEmpty())
        assert(graph.getDirectDependents("2") == setOf("1"))

        graph.addEdge("2", "3")
        assert(graph.getNodes() == setOf("1", "2", "3"))
        assert(graph.getDirectDependents("1").isEmpty())
        assert(graph.getDirectDependents("2") == setOf("1"))
        assert(graph.getDirectDependents("3") == setOf("2"))
    }

    @Test
    fun `remove nodes`() {
        val graph = MutableDependencyGraph<String>()
        graph.addEdge("1", "2")
        graph.addEdge("2", "3")
        graph.addEdge("1", "3")
        graph.removeNode("2")

        assert(graph.getNodes() == setOf("1", "3"))
        assert(graph.getDirectDependents("1").isEmpty())
        assert(graph.getDirectDependents("2").isEmpty())
        assert(graph.getDirectDependents("3") == setOf("1"))
    }

    @Test
    fun `get all dependent nodes`() {
        val graph = MutableDependencyGraph<String>()
        graph.addEdge("1", "2")
        graph.addEdge("2", "3")
        graph.addEdge("4", "5")

        assert(graph.getNodes() == setOf("1", "2", "3", "4", "5"))
        assert(graph.getAllDependents(setOf("1")).isEmpty())
        assert(graph.getAllDependents(setOf("2")) == setOf("1"))
        assert(graph.getAllDependents(setOf("3")) == setOf("1", "2"))
        assert(graph.getAllDependents(setOf("4")).isEmpty())
        assert(graph.getAllDependents(setOf("5")) == setOf("4"))
        assert(graph.getAllDependents(setOf("1", "4")).isEmpty())
        assert(graph.getAllDependents(setOf("3", "5")) == setOf("1", "2", "4"))
    }
}