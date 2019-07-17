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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;

public class TestBase {
    protected static long[] ALIGNMENTS = {FreeStore.DEFAULT_ALIGNMENT, FreeStore.PAGE_ALIGNMENT};

    protected static final int BENCHMARK_SAMPLE_SIZE = 3;

    protected static final String RESOURCES_PATH = "tools/base/zipflinger/test/resource/";

    protected static Path getPath(String path) {
        return Paths.get(RESOURCES_PATH + path);
    }

    protected static File getFile(String path) {
        return new File(RESOURCES_PATH + path);
    }

    protected static Map<String, Entry> verifyArchive(File archiveFile) throws IOException {
        HashMap<String, ZipEntry> topDownEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archiveFile))) {
            byte[] buffer = new byte[10_240];
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                ByteArrayOutputStream fos = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
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
            Assert.assertTrue("Entry " + name + " match crc", crc == o.getCrc());
            Assert.assertTrue(
                    "Entry " + name + " match size",
                    e.getCompressedSize() == o.getCompressedSize());
            Assert.assertTrue(
                    "Entry " + name + " match usize", e.getUncompressedSize() == o.getSize());
        }

        return bottomUpEntries;
    }

    protected static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    protected byte[] toByteArray(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    protected void createZip(File archive, File[] files) throws IOException {
        if (archive.exists()) {
            archive.delete();
        }

        try (FileOutputStream f = new FileOutputStream(archive);
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (File file : files) {
                String name = file.getName();
                ZipEntry entry = new ZipEntry(name);
                byte[] bytes = Files.readAllBytes(file.toPath());
                s.putNextEntry(entry);
                s.write(bytes);
                s.closeEntry();
            }
        }
    }
}
