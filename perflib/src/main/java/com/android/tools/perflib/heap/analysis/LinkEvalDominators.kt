/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.perflib.heap.Instance
import com.android.tools.perflib.heap.RootObj
import com.android.tools.perflib.heap.Snapshot
import gnu.trove.TIntArrayList
import gnu.trove.TObjectIdentityHashingStrategy
import gnu.trove.TObjectIntHashMap
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Stack

/**
 * Computes dominators based on the union-find data structure with path compression and linking by
 * size. Using description found in: http://adambuchsbaum.com/papers/dom-toplas.pdf which is based
 * on a copy of the paper available at:
 * http://www.cc.gatech.edu/~harrold/6340/cs6340_fall2009/Readings/lengauer91jul.pdf
 */
class LinkEvalDominators(private val snapshot: Snapshot) {

    init {
        // Initialize retained sizes for all classes and objects, including unreachable ones.
        for (heap in snapshot.heaps) {
            heap.classes.forEach(Instance::resetRetainedSize)
            heap.forEachInstance {
                it.resetRetainedSize()
                true
            }
        }
    }

    /**
     * Computes retained sizes of instances. Only call this AFTER dominator computation.
     */
    fun computeRetainedSizes() {
        // We only update the retained sizes of objects in the dominator tree (i.e. reachable).
        // This loop relies on the fact that getReachableInstances returns the
        // nodes in topological order.
        for (node in snapshot.getReachableInstances().asReversed()) {
            val dom = node.immediateDominator
            if (dom !== Snapshot.SENTINEL_ROOT) {
                dom?.addRetainedSizes(node)
            }
        }
    }

    fun computeDominators() {
        // Step 1 of paper.
        // Number the instances by their DFS-traversal order and record each one's parent in the DFS
        // tree.
        // Also gather predecessors and initialize semi-dominators in the same pass.
        val (instances, parents, preds) = computeIndicesAndParents()
        val semis = IntArray(instances.size) { it }
        val buckets = Array(instances.size) { TIntArrayList() }
        val doms = IntArray(instances.size)
        val ancestors = IntArray(instances.size) { INVALID_ANCESTOR }
        val labels = IntArray(instances.size) { it }
        for (currentNode in instances.size - 1 downTo 1) {
            // Step 2 of paper.
            // Compute each instance's semi-dominator
            for (predecessor in preds[currentNode] ?: EMPTY_INT_ARRAY) {
                val evaledPredecessor = eval(ancestors, labels, semis, predecessor)
                if (semis[evaledPredecessor] < semis[currentNode]) {
                    semis[currentNode] = semis[evaledPredecessor]
                }
            }
            buckets[semis[currentNode]].add(currentNode)
            ancestors[currentNode] = parents[currentNode]

            // Step 3 of paper.
            // Implicitly define each node's immediate dominator by Corollary 1
            for (i in 0 until buckets[parents[currentNode]].size()) {
                val node = buckets[parents[currentNode]][i]
                val nodeEvaled = eval(ancestors, labels, semis, node)
                doms[node] =
                    if (semis[nodeEvaled] < semis[node]) nodeEvaled else parents[currentNode]
                instances[node].setImmediateDominator(instances[doms[node]])
            }
            buckets[parents[currentNode]].clear() // Bulk remove (slightly different from paper).
        }

        // Step 4 of paper.
        // Explicitly define each node's immediate dominator
        for (currentNode in 1 until instances.size) {
            if (doms[currentNode] != semis[currentNode]) {
                doms[currentNode] = doms[doms[currentNode]]
                instances[currentNode].setImmediateDominator(instances[doms[currentNode]])
            }
        }
    }

    /** Traverse the instances depth-first, marking their order and parents in the DFS-tree  */
    private fun computeIndicesAndParents(): DFSResult {
        val parents = TObjectIntHashMap<Instance>(TObjectIdentityHashingStrategy())
        val instances = ArrayList<Instance>()
        val nodeStack = Stack<Instance>()
        instances.add(Snapshot.SENTINEL_ROOT)
        val gcRoots = snapshot.gcRoots.mapNotNullTo(mutableSetOf(), RootObj::getReferredInstance)
        gcRoots.forEach { gcRoot ->
            parents.put(gcRoot, 0)
            nodeStack.push(gcRoot)
        }
        dfs(nodeStack, instances, parents)
        return DFSResult.of(instances, parents, gcRoots)
    }

    private data class DFSResult(
        val instances: Array<Instance>,
        val parents: IntArray, // Predecessors not involved in DFS, but lumped in here for 1 pass. Paper did same.
        val predecessors: Array<IntArray?>
    ) {
        companion object {
            fun of(
                instances: ArrayList<Instance>,
                parents: TObjectIntHashMap<Instance>,
                gcRoots: Set<Instance?>
            ): DFSResult {
                val parentIndices = IntArray(instances.size)
                val predIndices = arrayOfNulls<IntArray>(instances.size)
                for (i in 1 until instances.size) { // omit auxiliary root at [0]
                    val instance = instances[i]
                    val order = instance.topologicalOrder
                    parentIndices[order] = parents[instance]
                    val backRefs = instance.hardReverseReferences.stream()
                        .filter(Instance::isReachable)
                        .mapToInt(Instance::getTopologicalOrder)
                        .toArray()
                    predIndices[order] = if (gcRoots.contains(instance)) (0 + backRefs) else backRefs
                }
                return DFSResult(instances.toTypedArray(), parentIndices, predIndices)
            }
        }
    }

    companion object {
        private fun dfs(
            nodeStack: Stack<Instance>,
            instances: ArrayList<Instance>,
            parents: TObjectIntHashMap<Instance>
        ) {
            val touched = Collections.newSetFromMap(IdentityHashMap<Instance, Boolean>())
            while (!nodeStack.empty()) {
                val node = nodeStack.pop()
                if (node !in touched) {
                    node.topologicalOrder = instances.size
                    touched.add(node)
                    instances.add(node)
                }
                for (succ in node.hardForwardReferences) {
                    if (!touched.contains(succ)) {
                        parents.put(succ, node.topologicalOrder)
                        nodeStack.push(succ)
                    }
                }
            }
        }

        private fun eval(ancestors: IntArray, labels: IntArray, semis: IntArray, node: Int) =
            when (ancestors[node]) {
                INVALID_ANCESTOR -> node
                else -> compress(ancestors, labels, semis, node)
            }

        /** @return a node's evaluation after compression
         */
        private fun compress(ancestors: IntArray, labels: IntArray, semis: IntArray, node: Int): Int {
            val compressArray = TIntArrayList()
            assert(ancestors[node] != INVALID_ANCESTOR)
            var n = node
            while (ancestors[ancestors[n]] != INVALID_ANCESTOR) {
                compressArray.add(n)
                n = ancestors[n]
            }
            for (i in compressArray.size() - 1 downTo 0) {
                val toCompress = compressArray[i]
                val ancestor = ancestors[toCompress]
                assert(ancestor != INVALID_ANCESTOR)
                if (semis[labels[ancestor]] < semis[labels[toCompress]]) {
                    labels[toCompress] = labels[ancestor]
                }
                ancestors[toCompress] = ancestors[ancestor]
            }
            return labels[node]
        }

        // 0 would coincide with valid parent. Paper uses 0 because they count from 1.
        private const val INVALID_ANCESTOR = -1

        private val EMPTY_INT_ARRAY = IntArray(0)
    }
}

private operator fun Int.plus(ns: IntArray) = IntArray(ns.size + 1).also { out ->
    System.arraycopy(ns, 0, out, 1, ns.size)
    out[0] = this
}
