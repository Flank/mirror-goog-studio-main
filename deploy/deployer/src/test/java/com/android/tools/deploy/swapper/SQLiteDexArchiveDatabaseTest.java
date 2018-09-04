/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deploy.swapper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SQLiteDexArchiveDatabaseTest {

    private SQLiteDexArchiveDatabase createTestDb() throws Exception {
        TemporaryFolder tmpdir = new TemporaryFolder();
        tmpdir.create();
        return new SQLiteDexArchiveDatabase(tmpdir.newFile("test.db"));
    }

    @Test
    public void testSimple01() throws Exception {
        SQLiteDexArchiveDatabase db = createTestDb();
        int id = 0;

        ArrayList<Integer> fileList = new ArrayList<>();
        id = db.addDexFile(111, "classes01.dex");
        fileList.add(id);
        Assert.assertEquals(1, id);
        id = db.addDexFile(112, "classes02.dex");
        fileList.add(id);
        Assert.assertEquals(2, id);
        id = db.addDexFile(113, "classes03.dex");
        fileList.add(id);
        Assert.assertEquals(3, id);

        String apkCheckSum = "SERIOUSCHECKSUM";
        db.fillDexFileList(apkCheckSum, fileList);

        List<DexArchiveDatabase.DexFileEntry> dexFiles = db.getDexFiles(apkCheckSum);
        Assert.assertEquals(3, dexFiles.size());
        dexFiles.sort((a, b) -> a.index - b.index);

        Assert.assertEquals(1, dexFiles.get(0).index);
        Assert.assertEquals(2, dexFiles.get(1).index);
        Assert.assertEquals(3, dexFiles.get(2).index);

        Assert.assertEquals(111, dexFiles.get(0).checksum);
        Assert.assertEquals(112, dexFiles.get(1).checksum);
        Assert.assertEquals(113, dexFiles.get(2).checksum);

        Assert.assertEquals("classes01.dex", dexFiles.get(0).name);
        Assert.assertEquals("classes02.dex", dexFiles.get(1).name);
        Assert.assertEquals("classes03.dex", dexFiles.get(2).name);

        Map<String, Long> classesChecksums = new LinkedHashMap<>();
        classesChecksums.put("testing.test1.A", 0xAl);
        classesChecksums.put("testing.test1.B", 0xBl);
        classesChecksums.put("testing.test1.C", 0xCl);

        db.fillEntriesChecksum(1, classesChecksums);

        classesChecksums = db.getClassesChecksum(1);
        Assert.assertEquals(0xAl, classesChecksums.get("testing.test1.A").longValue());
        Assert.assertEquals(0xBl, classesChecksums.get("testing.test1.B").longValue());
        Assert.assertEquals(0xCl, classesChecksums.get("testing.test1.C").longValue());
    }

    @Test
    public void testOutdatedSchema() throws Exception {
        TemporaryFolder tmdir = new TemporaryFolder();
        tmdir.create();
        File file = tmdir.newFile("test.db");
        SQLiteDexArchiveDatabase db = new SQLiteDexArchiveDatabase(file, "OUTDATED");

        int id = 0;
        ArrayList<Integer> fileList = new ArrayList<>();
        id = db.addDexFile(111, "classes01.dex");
        fileList.add(id);

        String apkCheckSum = "SERIOUSCHECKSUM";
        db.fillDexFileList(apkCheckSum, fileList);

        List<DexArchiveDatabase.DexFileEntry> dexFiles = db.getDexFiles(apkCheckSum);
        Assert.assertEquals(1, dexFiles.size());

        Assert.assertEquals(1, dexFiles.get(0).index);
        Assert.assertEquals("classes01.dex", dexFiles.get(0).name);
        db.close();

        db = new SQLiteDexArchiveDatabase(file, "CURRENT");
        dexFiles = db.getDexFiles(apkCheckSum);

        // All the tables are dropped version mismatched.
        Assert.assertEquals(0, dexFiles.size());
    }

    @Test
    public void testInvalidIndex() throws Exception {
        SQLiteDexArchiveDatabase db = createTestDb();

        int index = db.getDexFileIndex(1);
        Assert.assertEquals(-1, index);

        Map<String, Long> classes = db.getClassesChecksum(1);
        Assert.assertTrue(classes.isEmpty());
    }

    @Test
    public void testFlushOldCache() throws Exception {
        SQLiteDexArchiveDatabase db = createTestDb();
        int firstChecksum = 1;
        int first = db.addDexFile(firstChecksum, "dexfile_1");
        Assert.assertEquals(1, first);
        ArrayList<Integer> list = new ArrayList<>();
        list.add(first);
        db.fillDexFileList("SERIOUSCHECKSUM", list);
        Map<String, Long> classes = new HashMap<>();
        classes.put("a.b.C", 1l);
        db.fillEntriesChecksum(first, classes);

        // Verify everything is inserted.
        int index = db.getDexFileIndex(firstChecksum);
        Assert.assertEquals(first, index);
        List<DexArchiveDatabase.DexFileEntry> files = db.getDexFiles("SERIOUSCHECKSUM");
        Assert.assertFalse(files.isEmpty());
        classes = db.getClassesChecksum(first);
        Assert.assertFalse(classes.isEmpty());

        for (int i = 0; i < SQLiteDexArchiveDatabase.MAX_DEXFILES_ENTRY; i++) {
            db.addDexFile(i + 2, "dexfile_" + (i + 2));
        }

        // The first entry should be gone now.
        index = db.getDexFileIndex(firstChecksum);
        Assert.assertEquals(-1, index);

        // Everything related to the first dex file should be gone by now.
        files = db.getDexFiles("SERIOUSCHECKSUM");
        Assert.assertTrue(files.isEmpty());
        classes = db.getClassesChecksum(first);
        Assert.assertTrue(classes.isEmpty());
    }
}
