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

import com.android.testutils.TestUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractZipflingerTest {
    protected static final long[] ALIGNMENTS = {
        FreeStore.DEFAULT_ALIGNMENT, FreeStore.PAGE_ALIGNMENT
    };

    protected static final String BASE = "tools/base/zipflinger/test/resource/";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected static Path getPath(String filename) {
        String fullPath = BASE + filename;
        Path prospect = Paths.get(fullPath);
        if (Files.exists(prospect)) {
            return prospect;
        }
        return TestUtils.resolveWorkspacePath(fullPath);
    }

    protected Path getTestPath(String filename) throws IOException {
        return temporaryFolder.newFolder().toPath().resolve(filename);
    }

    protected static Map<String, Entry> verifyArchive(Path archiveFile) throws IOException {
        HashMap<String, ZipEntry> topDownEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archiveFile))) {
            byte[] buffer = new byte[10_240];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                while (zis.read(buffer) > 0) ;
                if (!zipEntry.getName().isEmpty()) {
                    topDownEntries.put(zipEntry.getName(), zipEntry);
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        // TODO: Compare entries parsed with bottom-up instead of returning them.
        Map<String, Entry> bottomUpEntries = ZipArchive.listEntries(archiveFile);

        Assert.assertEquals(
                "Bottom-up and Top-down # entries don't match",
                topDownEntries.size(),
                bottomUpEntries.size());

        for (String name : bottomUpEntries.keySet()) {
            Assert.assertTrue(
                    "Top-down should contain entry " + name, topDownEntries.containsKey(name));
        }

        for (String name : topDownEntries.keySet()) {
            Assert.assertTrue(
                    "Bottom-up should contain entry " + name, bottomUpEntries.containsKey(name));
        }

        // TODO: Compare sizes and crcs
        for (String name : bottomUpEntries.keySet()) {
            Entry e = bottomUpEntries.get(name);
            ZipEntry o = topDownEntries.get(name);
            long crc = e.getCrc() & 0xFFFFFFFFL;
            Assert.assertEquals("Entry " + name + " crcs don't match", crc, o.getCrc());
            Assert.assertEquals(
                    "Entry " + name + " match size", e.getCompressedSize(), o.getCompressedSize());
            Assert.assertEquals(
                    "Entry " + name + " match usize", e.getUncompressedSize(), o.getSize());
        }

        return bottomUpEntries;
    }

    protected static byte[] toByteArray(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    protected static void createZip(Path archive, Path[] files) throws IOException {
        Files.deleteIfExists(archive);

        try (OutputStream f = Files.newOutputStream(archive);
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = Files.readAllBytes(file);
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
        }
    }

    static void createZip(long numFiles, int sizePerFile, Path file) throws IOException {
        Files.deleteIfExists(file);

        long fileId = 0;
        try (OutputStream f = Files.newOutputStream(file);
                ZipOutputStream s = new ZipOutputStream(f)) {
            s.setLevel(ZipOutputStream.STORED);
            for (int i = 0; i < numFiles; i++) {
                long id = fileId++;
                String name = String.format("file%06d", id);
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = new byte[sizePerFile];
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
        }
    }
}
