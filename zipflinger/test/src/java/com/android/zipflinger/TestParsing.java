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

import java.io.File;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class TestParsing extends TestBase {

    @Test
    public void testMapWithoutDataDescriptors() throws Exception {
        ZipMap map = ZipMap.from(getFile("zip_no_fd.zip"), true);
        Map<String, Entry> entries = map.getEntries();

        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("First entry location", new Location(0, 67), entry.getLocation());

        entry = entries.get("empty2.txt");
        Assert.assertEquals("First entry location", new Location(67, 68), entry.getLocation());
    }

    @Test
    public void testZipWithDataDescriptors() throws Exception {
        ZipMap map = ZipMap.from(getFile("zip_with_fd.zip"), true);
        Map<String, Entry> entries = map.getEntries();
        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("First entry location", new Location(0, 67 + 16), entry.getLocation());

        entry = entries.get("empty2.txt");
        Assert.assertEquals("First entry location", new Location(83, 84), entry.getLocation());
    }

    @Test
    public void testDataDescriptorInvalideLocation() throws Exception {
        ZipMap map = ZipMap.from(getFile("zip_with_fd.zip"), false);
        Map<String, Entry> entries = map.getEntries();
        Entry entry = entries.get("empty.txt");
        Assert.assertEquals("Entry is valid", entry.getLocation(), Location.INVALID);
    }

    @Test
    public void testZipWithLargeEntriesAndDataDescriptors() throws Exception {
        File target = getTestFile("largeEntriesDD.zip");
        createZip(42, 1_000_000, target);
        ZipMap map = ZipMap.from(target, true);
        map.getEntries();
    }

    // Gradle Plug-in features a "resource stripped" which generates invalid extra field (e.g:size=1)
    // Namely, they do not feature a valid ID-size-payload combination.
    @Test
    public void testStripped() throws Exception {
        ZipMap map = ZipMap.from(getFile("stripped.ap_"), true);
    }
}
