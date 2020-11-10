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

import java.nio.file.Path;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class ZipMergeTest extends AbstractZipflingerTest {
    // Test 64 not allowed
    // Test merge zip into an other one
    // Test create zip and merge an other one
    // Test skipper
    // Test relocator
    @Test
    public void testMergeZips() throws Exception {
        Path dst = getTestPath("newArchive.zip");

        ZipArchive zipArchive = new ZipArchive(dst);

        ZipSource zs1 = ZipSource.selectAll(getPath("1-2-3files.zip"));
        zipArchive.add(zs1);

        ZipSource zs2 = ZipSource.selectAll(getPath("4-5files.zip"));
        zipArchive.add(zs2);

        zipArchive.close();

        Map<String, Entry> entries = ZipArchive.listEntries(dst);
        Assert.assertEquals("Num entries", 5, entries.size());
        Assert.assertTrue("Entries contains file1.txt", entries.containsKey("file1.txt"));
        Assert.assertTrue("Entries contains file2.txt", entries.containsKey("file2.txt"));
        Assert.assertTrue("Entries contains file3.txt", entries.containsKey("file3.txt"));
        Assert.assertTrue("Entries contains file4.txt", entries.containsKey("file4.txt"));
        Assert.assertTrue("Entries contains file5.txt", entries.containsKey("file5.txt"));

        // Topdown parsing with SDK zip.
        verifyArchive(dst);
    }
}
