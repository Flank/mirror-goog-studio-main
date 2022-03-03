/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.testutils.TestResources;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.SnapshotBuilder;
import junit.framework.TestCase;

import java.io.File;
import java.util.stream.Stream;

// These are now tests on computing retained sizes and path-to-gc, rather than the lower-level
// dominator algorithm
public class DominatorsTest extends TestCase {

    private Snapshot mSnapshot;

    public void testSimpleGraph() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 2, 3)
                .addReferences(2, 4, 6)
                .addReferences(3, 4, 5)
                .addReferences(4, 6)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(6, mSnapshot.getReachableInstances().size());

        assertParentPathToGc(2, 1);
        assertParentPathToGc(3, 1);
        assertParentPathToGc(4, 2, 3);
        assertParentPathToGc(5, 3);
        assertParentPathToGc(6, 2);
    }

    public void testCyclicGraph() {
        mSnapshot = new SnapshotBuilder(4)
                .addReferences(1, 2, 3, 4)
                .addReferences(2, 3)
                .addReferences(3, 4)
                .addReferences(4, 2)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(4, mSnapshot.getReachableInstances().size());

        assertParentPathToGc(2, 1);
        assertParentPathToGc(3, 1);
        assertParentPathToGc(4, 1);
    }

    public void testMultipleRoots() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 3)
                .addReferences(2, 4)
                .addReferences(3, 5)
                .addReferences(4, 5)
                .addReferences(5, 6)
                .addRoot(1)
                .addRoot(2)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(6, mSnapshot.getReachableInstances().size());

        assertParentPathToGc(3, 1);
        assertParentPathToGc(4, 2);
        assertParentPathToGc(5, 3, 4);
        assertParentPathToGc(6, 5);
    }

    public void testDoublyLinkedList() {
        // Node 1 points to a doubly-linked list 2-3-4-5-6-7-8-9.
        mSnapshot = new SnapshotBuilder(9)
                .addReferences(1, 2)
                .addReferences(2, 3, 9)
                .addReferences(3, 2, 4)
                .addReferences(4, 3, 5)
                .addReferences(5, 4, 6)
                .addReferences(6, 5, 7)
                .addReferences(7, 6, 8)
                .addReferences(8, 7, 9)
                .addReferences(9, 2, 8)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(45, mSnapshot.findInstance(1).getRetainedSize(1));
        assertEquals(44, mSnapshot.findInstance(2).getRetainedSize(1));
        for (int i = 3; i <= 9; i++) {
            assertEquals(i, mSnapshot.findInstance(i).getRetainedSize(1));
        }

        assertParentPathToGc(2, 1);
        assertParentPathToGc(3, 2);
        assertParentPathToGc(9, 2);
        assertParentPathToGc(4, 3);
        assertParentPathToGc(8, 9);
        assertParentPathToGc(5, 4);
        assertParentPathToGc(7, 8);
        assertParentPathToGc(6, 5, 7);
    }

    public void testSameClassDifferentLoader() {
        mSnapshot = new SnapshotBuilder(4)
                .addReferences(1, 3, 2)
                .addReferences(3, 2)
                .addRoot(1)
                .build();

        assertNotNull(mSnapshot.getHeap(13).getClass(102));
        assertNotNull(mSnapshot.getHeap(13).getClass(103));

        mSnapshot.computeRetainedSizes();

        assertEquals(0, mSnapshot.getHeap(13).getClass(102).getRetainedSize(1));
        assertEquals(0, mSnapshot.getHeap(13).getClass(103).getRetainedSize(1));
    }

    public void testTopSort() {
        mSnapshot = new SnapshotBuilder(4)
                .addReferences(1, 3, 2)
                .addReferences(3, 2)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(6, mSnapshot.findInstance(1).getRetainedSize(1));
        assertEquals(2, mSnapshot.findInstance(2).getRetainedSize(1));
        assertEquals(3, mSnapshot.findInstance(3).getRetainedSize(1));
    }

    public void testMultiplePaths() {
        mSnapshot = new SnapshotBuilder(8)
                .addReferences(1, 7, 8)
                .addReferences(7, 2, 3)
                .addReferences(8, 2)
                .addReferences(2, 4)
                .addReferences(3, 5)
                .addReferences(5, 4)
                .addReferences(4, 6)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();

        assertEquals(36, mSnapshot.findInstance(1).getRetainedSize(1));
        assertEquals(2, mSnapshot.findInstance(2).getRetainedSize(1));
        assertEquals(8, mSnapshot.findInstance(3).getRetainedSize(1));
    }

    public void testReachableInstances() {
        mSnapshot = new SnapshotBuilder(11, 2, 1)
                .addReferences(1, 2, 3)
                .insertSoftReference(1, 11)
                .addReferences(2, 4)
                .addReferences(3, 5, 6)
                .insertSoftReference(4, 9)
                .addReferences(5, 7)
                .addReferences(6, 7)
                .addReferences(7, 8, 10)
                .insertSoftAndHardReference(8, 10, 9)
                .addRoot(1)
                .build();

        mSnapshot.computeRetainedSizes();
        for (Heap heap : mSnapshot.getHeaps()) {
            ClassObj softClass = heap.getClass(SnapshotBuilder.SOFT_REFERENCE_ID);
            if (softClass != null) {
                assertTrue(softClass.isSoftReference());
            }

            ClassObj softAndHardClass = heap.getClass(SnapshotBuilder.SOFT_AND_HARD_REFERENCE_ID);
            if (softAndHardClass != null) {
                assertTrue(softAndHardClass.isSoftReference());
            }
        }

        Instance instance9 = mSnapshot.findInstance(9);
        assertNotNull(instance9);
        assertNotNull(instance9.getSoftReverseReferences());
        assertEquals(1, instance9.getHardReverseReferences().size());
        assertEquals(1, instance9.getSoftReverseReferences().size());
        assertEquals(6, instance9.getDistanceToGcRoot());

        Instance instance10 = mSnapshot.findInstance(10);
        assertNotNull(instance10);
        assertNotNull(instance10.getSoftReverseReferences());
        assertEquals(1, instance10.getHardReverseReferences().size());
        assertEquals(1, instance10.getSoftReverseReferences().size());
        assertEquals(4, instance10.getDistanceToGcRoot());

        Instance instance11 = mSnapshot.findInstance(11);
        assertNotNull(instance11);
        assertNotNull(instance11.getSoftReverseReferences());
        assertEquals(0, instance11.getHardReverseReferences().size());
        assertEquals(1, instance11.getSoftReverseReferences().size());
        assertEquals(Integer.MAX_VALUE, instance11.getDistanceToGcRoot());

        assertEquals(13, mSnapshot.getReachableInstances().size());
    }

    public void testSampleHprof() throws Exception {
        File file = TestResources.getFile(getClass(), "/dialer.android-hprof");
        mSnapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        mSnapshot.computeRetainedSizes();

        long totalInstanceCount = 0;
        for (Heap heap : mSnapshot.getHeaps()) {
            totalInstanceCount += heap.getInstancesCount();
            totalInstanceCount += heap.getClasses().size();
        }
        assertEquals(43687, totalInstanceCount);

        assertEquals(42868, mSnapshot.getReachableInstances().size());

        // An object reachable via two GC roots, a JNI global and a Thread.
        Instance instance = mSnapshot.findInstance(0xB0EDFFA0);

        int appIndex = mSnapshot.getHeapIndex(mSnapshot.getHeap("app"));
        int zygoteIndex = mSnapshot.getHeapIndex(mSnapshot.getHeap("zygote"));

        // The largest object in our sample hprof belongs to the zygote
        ClassObj htmlParser = mSnapshot.findClass("android.text.Html$HtmlParser");
        assertEquals(116492, htmlParser.getRetainedSize(zygoteIndex));
        assertEquals(0, htmlParser.getRetainedSize(appIndex));

        // One of the bigger objects in the app heap
        ClassObj activityThread = mSnapshot.findClass("android.app.ActivityThread");
        assertEquals(853, activityThread.getRetainedSize(zygoteIndex));
        assertEquals(576, activityThread.getRetainedSize(appIndex));

        mSnapshot.dispose();
        mSnapshot = null;
    }

    /**
     * Asserts that one of the parents is the direct parent to node in the shortest path to the GC root.
     */
    private void assertParentPathToGc(int node, int... parents) {
        for (int parent : parents) {
            Instance instance = mSnapshot.findInstance(node);
            Stream<Instance> parentInstances = instance.getHardReverseReferences().stream()
                    .filter(next -> next.getDistanceToGcRoot() < instance.getDistanceToGcRoot());
            if (parentInstances.anyMatch(p -> p.getId() == parent)) {
                return;
            }
        }
        fail();
    }
}
