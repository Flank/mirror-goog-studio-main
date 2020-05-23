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
package com.android.tools.perflib.heap.analysis;

import com.android.annotations.NonNull;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.android.tools.perflib.heap.Snapshot;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Computes dominators based on the union-find data structure with path compression and linking by
 * size. Using description found in: http://adambuchsbaum.com/papers/dom-toplas.pdf which is based
 * on a copy of the paper available at:
 * http://www.cc.gatech.edu/~harrold/6340/cs6340_fall2009/Readings/lengauer91jul.pdf
 */
public final class LinkEvalDominators extends DominatorsBase {

    private volatile int mNodeCount;
    private volatile int mSemiDominatorProgress = 0;
    private volatile int mDominatorProgress = 0;

    public LinkEvalDominators(@NonNull Snapshot snapshot) {
        super(snapshot);
    }

    @NonNull
    @Override
    public ComputationProgress getComputationProgress() {
        String progressMessage;
        double progress;
        if (mSemiDominatorProgress < mNodeCount) {
            progressMessage =
                    String.format(
                            "Calculating semi-dominators %d/%d",
                            mSemiDominatorProgress, mNodeCount);
            progress = 0.5 * (double) mSemiDominatorProgress / (double) mNodeCount;
        } else {
            progressMessage =
                    String.format(
                            "Calculating immediate dominators %d/%d",
                            mDominatorProgress, mNodeCount);
            progress = 0.5 + 0.5 * (double) mDominatorProgress / (double) mNodeCount;
        }
        mCurrentProgress.setMessage(progressMessage);
        mCurrentProgress.setProgress(progress);
        return mCurrentProgress;
    }

    @Override
    public void computeDominators() {
        // Step 1 of paper.
        // Number the instances by their DFS-traversal order and record each one's parent in the DFS
        // tree.
        // Also gather predecessors and initialize semi-dominators in the same pass.
        DFSResult result = computeIndicesAndParents();
        Instance[] instances = result.instances;
        int[] parents = result.parents;
        int[][] preds = result.predecessors;
        int[] semis = makeIdentityIntArray(instances.length);
        TIntArrayList[] buckets = new TIntArrayList[instances.length];
        for (int i = 0; i < buckets.length; ++i) {
            buckets[i] = new TIntArrayList();
        }
        int[] doms = new int[instances.length];
        int[] ancestors = new int[instances.length];
        Arrays.fill(ancestors, INVALID_ANCESTOR);
        int[] labels = makeIdentityIntArray(instances.length);
        mNodeCount = instances.length;

        for (int currentNode = instances.length - 1; currentNode > 0; --currentNode) {
            mSemiDominatorProgress = instances.length - currentNode;

            // Step 2 of paper.
            // Compute each instance's semi-dominator
            for (int predecessor : preds[currentNode]) {
                int evaledPredecessor = eval(ancestors, labels, semis, predecessor);
                if (semis[evaledPredecessor] < semis[currentNode]) {
                    semis[currentNode] = semis[evaledPredecessor];
                }
            }
            buckets[semis[currentNode]].add(currentNode);
            ancestors[currentNode] = parents[currentNode];

            // Step 3 of paper.
            // Implicitly define each node's immediate dominator by Corollary 1
            for (int i = 0; i < buckets[parents[currentNode]].size(); ++i) {
                int node = buckets[parents[currentNode]].get(i);
                int nodeEvaled = eval(ancestors, labels, semis, node);
                doms[node] = semis[nodeEvaled] < semis[node] ? nodeEvaled : parents[currentNode];
                instances[node].setImmediateDominator(instances[doms[node]]);
            }
            buckets[parents[currentNode]].clear(); // Bulk remove (slightly different from paper).
        }

        // Step 4 of paper.
        // Explicitly define each node's immediate dominator
        for (int currentNode = 1; currentNode < instances.length; ++currentNode) {
            if (doms[currentNode] != semis[currentNode]) {
                doms[currentNode] = doms[doms[currentNode]];
                instances[currentNode].setImmediateDominator(instances[doms[currentNode]]);
            }
            mDominatorProgress = currentNode;
        }
    }

    /** Traverse the instances depth-first, marking their order and parents in the DFS-tree */
    private DFSResult computeIndicesAndParents() {
        TObjectIntHashMap<Instance> parents =
                new TObjectIntHashMap<>(TObjectHashingStrategy.IDENTITY);
        ArrayList<Instance> instances = new ArrayList<>();
        Stack<Instance> nodeStack = new Stack<>();
        instances.add(Snapshot.SENTINEL_ROOT);

        Set<Instance> gcRoots =
                mSnapshot.getGCRoots().stream()
                        .map(RootObj::getReferredInstance)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        gcRoots.forEach(
                gcRoot -> {
                    parents.put(gcRoot, 0);
                    nodeStack.push(gcRoot);
                });

        dfs(nodeStack, instances, parents);
        return DFSResult.of(instances, parents, gcRoots);
    }

    private static void dfs(
            Stack<Instance> nodeStack,
            ArrayList<Instance> instances,
            TObjectIntHashMap<Instance> parents) {
        Set<Instance> touched = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!nodeStack.empty()) {
            Instance node = nodeStack.pop();
            if (!touched.contains(node)) {
                node.setTopologicalOrder(instances.size());
                touched.add(node);
                instances.add(node);
            }

            for (Instance succ : node.getHardForwardReferences()) {
                if (!touched.contains(succ)) {
                    parents.put(succ, node.getTopologicalOrder());
                    nodeStack.push(succ);
                }
            }
        }
    }

    private static int eval(int[] ancestors, int[] labels, int[] semis, int node) {
        return ancestors[node] == INVALID_ANCESTOR
                ? node
                : compress(ancestors, labels, semis, node);
    }

    /** @return a node's evaluation after compression */
    private static int compress(int[] ancestors, int[] labels, int[] semis, int node) {
        TIntArrayList compressArray = new TIntArrayList();

        assert ancestors[node] != INVALID_ANCESTOR;
        for (int n = node; ancestors[ancestors[n]] != INVALID_ANCESTOR; n = ancestors[n]) {
            compressArray.add(n);
        }
        for (int i = compressArray.size() - 1; i >= 0; --i) {
            int toCompress = compressArray.get(i);
            int ancestor = ancestors[toCompress];
            assert ancestor != INVALID_ANCESTOR;
            if (semis[labels[ancestor]] < semis[labels[toCompress]]) {
                labels[toCompress] = labels[ancestor];
            }
            ancestors[toCompress] = ancestors[ancestor];
        }
        return labels[node];
    }

    private static int[] makeIdentityIntArray(int size) {
        int[] ints = new int[size];
        for (int i = 0; i < size; ++i) {
            ints[i] = i;
        }
        return ints;
    }

    private static class DFSResult {
        final Instance[] instances;
        final int[] parents;
        // Predecessors not involved in DFS, but lumped in here for 1 pass. Paper did same.
        final int[][] predecessors;

        DFSResult(Instance[] instances, int[] parents, int[][] predecessors) {
            this.instances = instances;
            this.parents = parents;
            this.predecessors = predecessors;
        }

        static DFSResult of(
                ArrayList<Instance> instances,
                TObjectIntHashMap<Instance> parents,
                Set<Instance> gcRoots) {
            int[] parentIndices = new int[instances.size()];
            int[][] predIndices = new int[instances.size()][];
            for (int i = 1; i < instances.size(); ++i) { // omit auxiliary root at [0]
                Instance instance = instances.get(i);
                int order = instance.getTopologicalOrder();
                parentIndices[order] = parents.get(instance);
                int[] backRefs =
                        instance.getHardReverseReferences().stream()
                                .filter(Instance::isReachable)
                                .mapToInt(Instance::getTopologicalOrder)
                                .toArray();
                predIndices[order] = gcRoots.contains(instance) ? prepend(0, backRefs) : backRefs;
            }
            return new DFSResult(instances.toArray(new Instance[0]), parentIndices, predIndices);
        }

        /**
         * @param n The integer to be the first element
         * @param ns The array containing the remaining elements
         * @return A new integer array with the given number prepended into the given array
         */
        private static int[] prepend(int n, int[] ns) {
            int[] ns1 = new int[ns.length + 1];
            System.arraycopy(ns, 0, ns1, 1, ns.length);
            ns1[0] = n;
            return ns1;
        }
    }

    // 0 would coincide with valid parent. Paper uses 0 because they count from 1.
    private static final int INVALID_ANCESTOR = -1;
}
