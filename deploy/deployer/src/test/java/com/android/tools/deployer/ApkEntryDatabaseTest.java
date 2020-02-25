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
package com.android.tools.deployer;

import static com.android.tools.deployer.ApkTestUtils.*;

import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.DexClass;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApkEntryDatabaseTest {

    private static final Apk apk = Apk.builder().setName("a.apk").setChecksum("ABCD").build();

    private SqlApkFileDatabase createTestDb(String version, int maxDexes) throws Exception {
        TemporaryFolder tmpdir = new TemporaryFolder();
        tmpdir.create();
        return new SqlApkFileDatabase(tmpdir.newFile("test.db"), null, version, maxDexes);
    }

    @Test
    public void testSimple01() throws Exception {
        SqlApkFileDatabase db = createTestDb("1.0", 10);

        ApkEntry classes01 = new ApkEntry("01.dex", 1234, apk);
        ApkEntry classes02 = new ApkEntry("02.dex", 1235, apk);
        DexClass c1 = new DexClass("A.1", 0xA1, null, classes01);
        DexClass c2 = new DexClass("B.1", 0xB1, null, classes01);
        DexClass c3 = new DexClass("B.2", 0XB2, null, classes02);

        db.addClasses(ImmutableList.of(c1, c2, c3));

        List<DexClass> classes = db.getClasses(classes02);
        Assert.assertEquals(1, classes.size());
        DexClass clazz = classes.get(0);
        Assert.assertEquals("B.2", clazz.name);
        Assert.assertEquals(0xB2, clazz.checksum);
        Assert.assertEquals(classes02, clazz.dex);
        Assert.assertNull(clazz.code);

        List<DexClass> dump = db.dump();
        Assert.assertEquals(3, dump.size());
        assertDexClassEquals("ABCD", "01.dex", 1234, "A.1", 0xA1, dump.get(0));
        assertDexClassEquals("ABCD", "01.dex", 1234, "B.1", 0xB1, dump.get(1));
        assertDexClassEquals("ABCD", "02.dex", 1235, "B.2", 0xB2, dump.get(2));

        Assert.assertFalse(db.hasDuplicates());
    }

    @Test
    public void testOutdatedSchema() throws Exception {
        TemporaryFolder tmdir = new TemporaryFolder();
        tmdir.create();
        File file = tmdir.newFile("test.db");
        SqlApkFileDatabase db = new SqlApkFileDatabase(file, null, "OUTDATED", 100);

        // Initialize the DB
        ApkEntry classes01 = new ApkEntry("01.dex", 1234, apk);
        ApkEntry classes02 = new ApkEntry("02.dex", 1235, apk);
        DexClass c1 = new DexClass("A.1", 0xA1, null, classes01);
        DexClass c2 = new DexClass("B.1", 0xB1, null, classes01);
        DexClass c3 = new DexClass("B.2", 0XB2, null, classes02);
        db.addClasses(ImmutableList.of(c1, c2, c3));

        List<DexClass> dump = db.dump();
        Assert.assertEquals(3, dump.size());

        db.close();
        db = new SqlApkFileDatabase(file, null, "CURRENT", 100);
        dump = db.dump();

        Assert.assertEquals(0, dump.size());
    }

    @Test
    public void testFlushOldCache() throws Exception {
        SqlApkFileDatabase db = createTestDb("1.0", 2);

        ApkEntry classes01 = new ApkEntry("01.dex", 1234, apk);
        ApkEntry classes02 = new ApkEntry("02.dex", 1235, apk);
        ApkEntry classes03 = new ApkEntry("03.dex", 1236, apk);
        DexClass c1 = new DexClass("A.1", 0xA1, null, classes01);

        // Add c1 to 01.dex
        db.addClasses(ImmutableList.of(c1));
        List<DexClass> dump = db.dump();
        Set<String> dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(1, dexes.size());
        Assert.assertTrue(dexes.contains("01.dex"));

        // Add c2 to 01.dex
        DexClass c2 = new DexClass("B.1", 0xB1, null, classes01);
        db.addClasses(ImmutableList.of(c2));
        dump = db.dump();
        dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(1, dexes.size());
        Assert.assertTrue(dexes.contains("01.dex"));

        // Add c3 to 02.dex
        DexClass c3 = new DexClass("B.2", 0XB2, null, classes02);
        db.addClasses(ImmutableList.of(c3));
        dump = db.dump();
        dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(2, dexes.size());
        Assert.assertTrue(dexes.contains("02.dex"));

        // Add c4 to 03.dex
        DexClass c4 = new DexClass("B.2", 0XB2, null, classes03);
        db.addClasses(ImmutableList.of(c4));
        dump = db.dump();
        dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(2, dexes.size());
        Assert.assertTrue(dexes.contains("02.dex"));
        Assert.assertTrue(dexes.contains("03.dex"));
        // 01.dex got flushed out and db should only have 02 and 03.
        Assert.assertFalse(dexes.contains("01.dex"));
    }

    @Test
    public void testExpandCacheSize() throws Exception {
        SqlApkFileDatabase db = createTestDb("1.0", 2);

        ApkEntry classes01 = new ApkEntry("01.dex", 1234, apk);
        ApkEntry classes02 = new ApkEntry("02.dex", 1235, apk);
        ApkEntry classes03 = new ApkEntry("03.dex", 1236, apk);
        ApkEntry classes04 = new ApkEntry("04.dex", 1236, apk);

        DexClass c1 = new DexClass("A.1", 0xA1, null, classes01);
        DexClass c2 = new DexClass("B.1", 0xB1, null, classes02);
        DexClass c3 = new DexClass("B.2", 0XB2, null, classes03);
        DexClass c4 = new DexClass("C.3", 0XC3, null, classes04);

        // Adding 01, 02, 03.dex to the database pushes the capacity
        // to 6.
        db.addClasses(ImmutableList.of(c1, c2, c3));
        List<DexClass> dump = db.dump();
        Set<String> dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(3, dexes.size());
        Assert.assertTrue(dexes.contains("01.dex"));
        Assert.assertTrue(dexes.contains("02.dex"));
        Assert.assertTrue(dexes.contains("03.dex"));

        // Adding more dex files shouldn't push thing out the database.
        db.addClasses(ImmutableList.of(c4));
        dump = db.dump();
        dexes = new HashSet<>();
        for (DexClass clazz : dump) {
            dexes.add(clazz.dex.getName());
        }
        Assert.assertEquals(4, dexes.size());
        Assert.assertTrue(dexes.contains("01.dex"));
        Assert.assertTrue(dexes.contains("02.dex"));
        Assert.assertTrue(dexes.contains("03.dex"));
        Assert.assertTrue(dexes.contains("04.dex"));

        Assert.assertFalse(db.hasDuplicates());
    }

    @Test
    public void testHasDuplicates() throws Exception {
        SqlApkFileDatabase db = createTestDb("1.0", 10);

        ApkEntry classes01 = new ApkEntry("01.dex", 1234, apk);
        DexClass c1 = new DexClass("A.1", 0xA1, null, classes01);
        DexClass c2 = new DexClass("A.1", 0xA1, null, classes01);

        db.addClasses(ImmutableList.of(c1, c2));

        List<DexClass> classes = db.getClasses(classes01);
        Assert.assertEquals(2, classes.size());
        DexClass clazz = classes.get(0);
        Assert.assertEquals("A.1", clazz.name);
        Assert.assertEquals(0xA1, clazz.checksum);
        Assert.assertNull(clazz.code);

        clazz = classes.get(1);
        Assert.assertEquals("A.1", clazz.name);
        Assert.assertEquals(0xA1, clazz.checksum);
        Assert.assertNull(clazz.code);

        List<DexClass> dump = db.dump();
        Assert.assertEquals(2, dump.size());
        assertDexClassEquals("ABCD", "01.dex", 1234, "A.1", 0xA1, dump.get(0));
        assertDexClassEquals("ABCD", "01.dex", 1234, "A.1", 0xA1, dump.get(1));

        Assert.assertTrue(db.hasDuplicates());
    }
}
