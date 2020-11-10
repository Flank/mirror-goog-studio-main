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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Verify {
    public static void main(String[] args) throws IOException {
        for (String archivePath : args) {
            verifyArchive(Paths.get(archivePath));
        }
    }

    private static Map<String, Entry> verifyArchive(Path archiveFile) throws IOException {
        System.out.println("Verifying: '" + archiveFile + "'");
        HashMap<String, ZipEntry> topDownEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archiveFile))) {
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

        Map<String, Entry> bottomUpEntries = ZipArchive.listEntries(archiveFile);

        if (topDownEntries.size() != bottomUpEntries.size()) {
            System.out.println("Bottom-up and Top-down # entries don't match!");
        } else {
            System.out.println(
                    "Both BU and TD parsing found " + topDownEntries.size() + " entries.");
        }

        for (String name : bottomUpEntries.keySet()) {
            if (!topDownEntries.keySet().contains(name)) {
                System.out.println(name + " found in bottom-up but not in top-down");
            }
        }

        for (String name : topDownEntries.keySet()) {
            if (!bottomUpEntries.keySet().contains(name)) {
                System.out.println(name + " found in top-down but not in bottom-up");
            }
        }

        // TODO: Compare sizes and crcs
        for (String name : bottomUpEntries.keySet()) {
            Entry e = bottomUpEntries.get(name);
            ZipEntry o = topDownEntries.get(name);
            long crc = e.getCrc() & 0xFFFFFFFFL;
            if (crc != o.getCrc()) {
                System.out.println("Entry " + name + " crcs don't match");
            }
            if (e.getCompressedSize() != o.getCompressedSize()) {
                System.out.println("Entry " + name + " compressed size don't match");
            }
            if (e.getUncompressedSize() != o.getSize()) {
                System.out.println("Entry " + name + " uncompressed size don't match");
            }
        }

        return bottomUpEntries;
    }
}
