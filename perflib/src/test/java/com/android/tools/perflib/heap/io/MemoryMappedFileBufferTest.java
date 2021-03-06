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

package com.android.tools.perflib.heap.io;

import static org.junit.Assert.assertArrayEquals;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;

import java.nio.file.Files;
import junit.framework.TestCase;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class MemoryMappedFileBufferTest extends TestCase {

    File file = TestResources.getFile(getClass(), "/dialer.android-hprof");

    public void testSimpleMapping() throws Exception {
        Snapshot snapshot = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
        assertSnapshotCorrect(snapshot);
        snapshot.dispose();
    }

    public void testMultiMapping() throws Exception {
        // Split the file into chunks of 4096 bytes each, leave 128 bytes for padding.
        MemoryMappedFileBuffer shardedBuffer = new MemoryMappedFileBuffer(file, 4096, 128);
        Snapshot snapshot = Snapshot.createSnapshot(shardedBuffer);
        assertSnapshotCorrect(snapshot);
        snapshot.dispose();
    }

    public void testMultiMappingWrappedRead() throws Exception {
        // Leave just 8 bytes for padding to force wrapped reads.
        MemoryMappedFileBuffer shardedBuffer = new MemoryMappedFileBuffer(file, 9973, 8);
        Snapshot snapshot = Snapshot.createSnapshot(shardedBuffer);
        assertSnapshotCorrect(snapshot);
        snapshot.dispose();
    }

    public void testMemoryMappingRemoval() throws Exception {
        File tmpFile = File.createTempFile("test_vm", ".tmp");
        System.err.println("vm temp file: " + tmpFile.getAbsolutePath());
        System.err.println("jvm " + System.getProperty("sun.arch.data.model"));

        long n = 500000000L;
        RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw");
        try {
            raf.setLength(n);
            raf.write(1);
            raf.seek(n - 1);
            raf.write(2);
        }
        finally {
            raf.close();
        }

        MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(tmpFile);
        assertEquals(1, buffer.readByte());
        buffer.setPosition(n - 1);
        assertEquals(2, buffer.readByte());

        // On Windows, tmpFile can't be deleted without unmapping it first.
        buffer.dispose();
        tmpFile.delete();

        File g = new File(tmpFile.getCanonicalPath());
        assertFalse(g.exists());
    }

    public void testSubsequenceReads() throws Exception {
        byte[] fileContents = Files.readAllBytes(file.toPath());
        MemoryMappedFileBuffer mappedBuffer = new MemoryMappedFileBuffer(file, 8259, 8);

        byte[] buffer = new byte[8190];
        mappedBuffer.readSubSequence(buffer, 0, 8190);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 0, 8190), buffer);
        assertEquals(8190, mappedBuffer.position());

        buffer = new byte[8190];
        mappedBuffer.setPosition(0);
        mappedBuffer.readSubSequence(buffer, 2000, 8190);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 2000, 2000 + 8190), buffer);
        assertEquals(2000 + 8190, mappedBuffer.position());

        buffer = new byte[100000];
        mappedBuffer.setPosition(0);
        mappedBuffer.readSubSequence(buffer, 19242, 100000);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 19242, 19242 + 100000), buffer);
        assertEquals(19242 + 100000, mappedBuffer.position());

        buffer = new byte[8259];
        mappedBuffer.setPosition(0);
        mappedBuffer.readSubSequence(buffer, 0, 8259);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 0, 8259), buffer);
        assertEquals(8259, mappedBuffer.position());

        buffer = new byte[8259];
        mappedBuffer.setPosition(0);
        mappedBuffer.readSubSequence(buffer, 8259, 8259);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 8259, 8259 + 8259), buffer);
        assertEquals(8259 + 8259, mappedBuffer.position());

        mappedBuffer.readSubSequence(buffer, 8259, 8259);
        assertArrayEquals(Arrays.copyOfRange(fileContents, 8259 * 3, 8259 * 4), buffer);
        assertEquals(8259 * 4, mappedBuffer.position());
    }

    private static void assertSnapshotCorrect(@NonNull Snapshot snapshot) {
        assertEquals(11193, snapshot.getGcRoots().size());
        assertEquals(38, snapshot.getHeap(65).getClasses().size());
        assertEquals(1406, snapshot.getHeap(65).getInstancesCount());
        assertEquals(3533, snapshot.getHeap(90).getClasses().size());
        assertEquals(38710, snapshot.getHeap(90).getInstancesCount());
    }
}
