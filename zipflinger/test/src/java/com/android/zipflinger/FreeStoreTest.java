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
package com.android.zipflinger;

import java.util.HashMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class FreeStoreTest extends AbstractZipflingerTest {

    private static class AllocRequest {
        long size;
        long offsetToPayload;
    }

    @Test
    public void testAlloc() {
        FreeStore store = new FreeStore(new HashMap<>());

        Location allocated = store.ualloc(4);
        Assert.assertEquals("First alloc Location size", 4, allocated.size());
        allocated = store.ualloc(1);
        Assert.assertEquals("Second alloc Location size", 1, allocated.size());
        allocated = store.ualloc(10);
        Assert.assertEquals("Third alloc Location size", 10, allocated.size());

        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(15, Long.MAX_VALUE - 15), freeLocations.get(0));
    }

    @Test
    public void testAllocZoneTooSmall() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(20);
        store.free(new Location(5, 5));

        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(5, 5), freeLocations.get(0));
        Assert.assertEquals(
                "Rest  Location", new Location(20, Long.MAX_VALUE - 20), freeLocations.get(1));

        store.ualloc(5);
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(5, 5), freeLocations.get(0));
        Assert.assertEquals(
                "Rest Location", new Location(25, Long.MAX_VALUE - 25), freeLocations.get(1));
    }

    @Test
    public void testAllocPerfectMatch() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(LocalFileHeader.VIRTUAL_HEADER_SIZE * 3);
        store.free(
                new Location(
                        LocalFileHeader.VIRTUAL_HEADER_SIZE,
                        LocalFileHeader.VIRTUAL_HEADER_SIZE + 5));
        store.ualloc(5);

        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals(
                "First Location",
                new Location(
                        LocalFileHeader.VIRTUAL_HEADER_SIZE + 5,
                        LocalFileHeader.VIRTUAL_HEADER_SIZE),
                freeLocations.get(0));
        Assert.assertEquals(
                "Rest  Location",
                new Location(
                        LocalFileHeader.VIRTUAL_HEADER_SIZE * 3,
                        Long.MAX_VALUE - LocalFileHeader.VIRTUAL_HEADER_SIZE * 3),
                freeLocations.get(1));
    }

    @Test
    public void testAllocMiddletMatch() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(200);
        store.free(new Location(20, 50));
        store.ualloc(10);

        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(30, 40), freeLocations.get(0));
        Assert.assertEquals(
                "Rest  Location", new Location(200, Long.MAX_VALUE - 200), freeLocations.get(1));
    }

    @Test
    public void testFree() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(20);

        // Test that there is only one free location which is the remaining of the file
        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(20, Long.MAX_VALUE - 20), freeLocations.get(0));

        store.free(new Location(0, 5));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(0, 5), freeLocations.get(0));
    }

    @Test
    public void testFreeMergingLeftRight() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(20);

        // Test that there is only one free location which is the remaining of the file
        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(20, Long.MAX_VALUE - 20), freeLocations.get(0));

        store.free(new Location(0, 5));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(0, 5), freeLocations.get(0));

        store.free(new Location(5, 5));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 2, freeLocations.size());
        Assert.assertEquals("First Location", new Location(0, 10), freeLocations.get(0));

        store.free(new Location(10, 10));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(0, Long.MAX_VALUE), freeLocations.get(0));
    }

    @Test
    public void testFreeMergingRightLeft() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(20);

        // Test that there is only one free location which is the remaining of the file
        List<Location> freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(20, Long.MAX_VALUE - 20), freeLocations.get(0));

        store.free(new Location(10, 10));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(10, Long.MAX_VALUE - 10), freeLocations.get(0));

        store.free(new Location(5, 5));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(5, Long.MAX_VALUE - 5), freeLocations.get(0));

        store.free(new Location(0, 5));
        freeLocations = store.getFreeLocations();
        Assert.assertEquals("Num free zones", 1, freeLocations.size());
        Assert.assertEquals(
                "First Location", new Location(0, Long.MAX_VALUE), freeLocations.get(0));
    }

    @Test
    public void testBadFree() {
        FreeStore store = new FreeStore(new HashMap<>());
        store.ualloc(20);

        boolean exceptionCaught = false;
        try {
            store.free(new Location(5, 20));
        } catch (IllegalStateException e) {
            exceptionCaught = true;
        }
        Assert.assertTrue("Unknown free did not throw an exception", exceptionCaught);
    }

    @Test
    public void testAlignment() {
        for (long alignment : ALIGNMENTS) {
            testAlignment(alignment);
        }
    }

    private void testAlignment(long alignment) {
        for (int offset = 0; offset < alignment; offset++) {
            Location allocated;
            AllocRequest allocationRequest = new AllocRequest();
            allocationRequest.size = 40;
            allocationRequest.offsetToPayload = offset;
            FreeStore store = new FreeStore(new HashMap<>());
            allocated =
                    store.alloc(
                            allocationRequest.size, allocationRequest.offsetToPayload, alignment);
            long padding = allocated.size() - allocationRequest.size;
            Assert.assertEquals(
                    "Aligned alloc size="
                            + allocationRequest.size
                            + " offset="
                            + offset
                            + ", alignement="
                            + alignment,
                    0,
                    (allocated.first + padding + allocationRequest.offsetToPayload) % alignment);
        }
    }

    @Test
    public void testMultipleAlignment() {
        for (long alignment : ALIGNMENTS) {
            testMultipleAlignment(alignment);
        }
    }

    private void testMultipleAlignment(long alignment) {
        Location allocated;
        AllocRequest allocationRequest = new AllocRequest();

        FreeStore store = new FreeStore(new HashMap<>());
        for (int offset = 0; offset < alignment; offset++) {
            allocationRequest.size = 40;
            allocationRequest.offsetToPayload = offset;
            allocated =
                    store.alloc(
                            allocationRequest.size, allocationRequest.offsetToPayload, alignment);
            long padding = allocated.size() - allocationRequest.size;
            Assert.assertEquals(
                    "Aligned alloc size="
                            + allocationRequest.size
                            + " offset="
                            + offset
                            + ",alignment="
                            + alignment,
                    0,
                    (allocated.first + padding + allocationRequest.offsetToPayload) % alignment);
        }
    }

    @Test
    public void testPadding() {
        int alignment = 4;
        for (long address = 0; address < alignment; address++) {
            for (long offset = 0; offset < alignment; offset++) {
                long padding = FreeStore.padFor(address, offset, alignment);
                Assert.assertEquals(
                        "Padding with address=" + address + ", offset=" + offset,
                        0L,
                        (address + offset + padding) % alignment,
                        0);
            }
        }
    }
}
