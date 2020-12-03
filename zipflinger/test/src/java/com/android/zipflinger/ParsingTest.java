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

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Test;

public class ParsingTest extends AbstractZipflingerTest {
    @Test
    public void testMapWithoutDataDescriptors() throws Exception {
        ZipMap map = ZipMap.from(getPath("zip_no_fd.zip"), true);
        Map<String, Entry> entries = map.getEntries();

        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("First entry location", new Location(0, 67), entry.getLocation());

        entry = entries.get("empty2.txt");
        Assert.assertEquals("First entry location", new Location(67, 68), entry.getLocation());
    }

    @Test
    public void testZipWithDataDescriptors() throws Exception {
        ZipMap map = ZipMap.from(getPath("zip_with_fd.zip"), true);
        Map<String, Entry> entries = map.getEntries();
        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("First entry location", new Location(0, 67 + 16), entry.getLocation());

        entry = entries.get("empty2.txt");
        Assert.assertEquals("First entry location", new Location(83, 84), entry.getLocation());
    }

    // Create a zip entry with four deflated entries and DDs. Delete the second and third entries.
    // If DD parsing/preservation was successful, the gap created by the deleted entry will be
    // properly filled by overwriting the DDs and ZipInputStream will be able to parse the entire
    // archive.
    @Test
    public void testZipWithDataDescriptorEditing() throws Exception {
        Path archiveFile = getTestPath("testZipWithDDEditing.zip");
        byte[] resourceBytes = new byte[1000];
        try (OutputStream f = Files.newOutputStream(archiveFile);
                ZipOutputStream s = new ZipOutputStream(f)) {
            for (int i = 0; i < 4; i++) {
                ZipEntry entry = new ZipEntry("file" + i);
                s.putNextEntry(entry);
                s.setMethod(ZipOutputStream.DEFLATED);
                s.write(resourceBytes);
                s.closeEntry();
            }
        }

        try (ZipArchive archive = new ZipArchive(archiveFile)) {
            archive.delete("file1");
        }

        try (ZipArchive archive = new ZipArchive(archiveFile)) {
            archive.delete("file2");
        }

        verifyArchive(archiveFile);
    }

    @Test
    public void testDataDescriptorInvalideLocation() throws Exception {
        ZipMap map = ZipMap.from(getPath("zip_with_fd.zip"), false);
        Map<String, Entry> entries = map.getEntries();
        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("Entry is valid", entry.getLocation(), Location.INVALID);
    }

    @Test
    public void testZipWithLargeEntriesAndDataDescriptors() throws Exception {
        Path target = getTestPath("largeEntriesDD.zip");
        createZip(42, 1_000_000, target);
        ZipMap map = ZipMap.from(target, true);
        map.getEntries();
    }

    // Gradle Plug-in features a "resource stripped" which generates invalid extra field (e.g:size=1)
    // Namely, they do not feature a valid ID-size-payload combination.
    @Test
    public void testStripped() throws Exception {
        ZipMap map = ZipMap.from(getPath("stripped.ap_"), true);
    }
}
