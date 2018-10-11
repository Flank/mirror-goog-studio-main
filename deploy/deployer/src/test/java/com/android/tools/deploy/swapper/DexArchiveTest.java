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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexArchiveTest {
    public interface TempDatabaseCreator {
        DexArchiveDatabase create() throws Exception;
    }

    @Parameterized.Parameter public TempDatabaseCreator dbCreator;

    /**
     * A collection of creators for temporary {@link SQLiteDexArchiveDatabase} and {@link
     * InMemoryDexArchiveDatabase}.
     */
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new TempDatabaseCreator[][] {
                    // In-memory DB
                    {() -> new InMemoryDexArchiveDatabase()},

                    // SQLite DB.
                    {
                        () -> {
                            TemporaryFolder tmpdir = new TemporaryFolder();
                            tmpdir.create();
                            return new SQLiteDexArchiveDatabase(
                                    tmpdir.newFile("test.db"), "TESTING-VERSION", 10);
                        }
                    },

                    // Work Queue with in-memory DB.
                    {
                        () -> {
                            return new WorkQueueDexArchiveDatabase(
                                    new InMemoryDexArchiveDatabase());
                        }
                    },

                    // Work Queue with SQLite DB.
                    {
                        () -> {
                            TemporaryFolder tmpdir = new TemporaryFolder();
                            tmpdir.create();
                            return new WorkQueueDexArchiveDatabase(
                                    new SQLiteDexArchiveDatabase(tmpdir.newFile("test.db")));
                        }
                    }
                });
    }

    /** Call to create a database of the current favor. */
    private DexArchiveDatabase getNewTestDb() throws Exception {
        return dbCreator.create();
    }

    @Test
    public void testSimpleCache() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        String hash = "hash01";

        // Create fake apk and cache it.
        Map<String, DexFile> dexfiles = makeDex(hash, "classes.dex");
        ((FakeDexFile) dexfiles.get("classes.dex")).addClass("com.example.Testing", 123);
        DexArchive apkOnDisk = new DexArchive(hash, dexfiles);
        apkOnDisk.cache(db);

        // Retrieve the apk and verify.
        DexArchive apkOnCache = db.retrieveCache(hash);
        Assert.assertEquals(
                "APK: hash01 code of classes.dex".hashCode(),
                apkOnCache.getDexFiles().get("classes.dex").getChecksum());
        Assert.assertEquals(1, apkOnCache.getDexFiles().size());
        Assert.assertEquals(
                123,
                apkOnCache
                        .getDexFiles()
                        .get("classes.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing")
                        .longValue());
    }

    @Test
    public void testEmptyApk() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        String hash = "hash01";

        // Create fake apk and cache it.
        Map<String, DexFile> dexfiles = makeDex(hash);
        DexArchive apkOnDisk = new DexArchive(hash, dexfiles);
        apkOnDisk.cache(db);

        // Retrieve the apk and verify.
        DexArchive apkOnCache = db.retrieveCache(hash);
        Assert.assertEquals(0, apkOnCache.getDexFiles().size());
    }

    @Test
    public void testEmptyDex() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        String hash = "hash01";

        // Create fake apk and cache it.
        Map<String, DexFile> dexfiles = makeDex(hash, "classes.dex");
        DexArchive apkOnDisk = new DexArchive(hash, dexfiles);
        apkOnDisk.cache(db);

        // Retrieve the apk and verify.
        DexArchive apkOnCache = db.retrieveCache(hash);
        Assert.assertEquals(
                "APK: hash01 code of classes.dex".hashCode(),
                apkOnCache.getDexFiles().get("classes.dex").getChecksum());
        Assert.assertEquals(1, apkOnCache.getDexFiles().size());
    }

    @Test
    public void testDoubleCacheSameApk() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        String hash = "hash01";

        // Create fake apk and cache it.
        Map<String, DexFile> dexfiles = makeDex(hash, "classes.dex");
        ((FakeDexFile) dexfiles.get("classes.dex")).addClass("com.example.Testing", 123);
        DexArchive apkOnDisk = new DexArchive(hash, dexfiles);
        apkOnDisk.cache(db);
        apkOnDisk.cache(db);

        // Retrieve the apk and verify.
        DexArchive apkOnCache = db.retrieveCache(hash);
        Assert.assertEquals(
                "APK: hash01 code of classes.dex".hashCode(),
                apkOnCache.getDexFiles().get("classes.dex").getChecksum());
        Assert.assertEquals(1, apkOnCache.getDexFiles().size());
        Assert.assertEquals(
                123,
                apkOnCache
                        .getDexFiles()
                        .get("classes.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing")
                        .longValue());
    }

    @Test
    public void testTwoApksUnchangedDexFile() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        String hash1 = "hash01";
        String hash2 = "hash02";

        // APK1
        Map<String, DexFile> dexfiles1 = makeDex(hash1, "classes.dex", "classes02.dex");
        ((FakeDexFile) dexfiles1.get("classes.dex")).addClass("com.example.Testing", 123);
        ((FakeDexFile) dexfiles1.get("classes02.dex")).addClass("com.example.Testing2", 345);
        DexArchive apk1OnDisk = new DexArchive(hash1, dexfiles1);
        apk1OnDisk.cache(db);

        // APK2
        Map<String, DexFile> dexfiles2 = makeDex(hash2, "classes.dex", "classes02.dex");
        ((FakeDexFile) dexfiles2.get("classes.dex")).addClass("com.example.Testing", 123);
        ((FakeDexFile) dexfiles2.get("classes02.dex")).addClass("com.example.Testing2", 567);
        DexArchive apk2OnDisk = new DexArchive(hash2, dexfiles2);
        apk2OnDisk.cache(db);

        // Retrieve and check
        DexArchive apk1OnCache = db.retrieveCache(hash1);
        DexArchive apk2OnCache = db.retrieveCache(hash2);

        Assert.assertEquals(
                "APK: hash01 code of classes.dex".hashCode(),
                apk1OnCache.getDexFiles().get("classes.dex").getChecksum());
        Assert.assertEquals(2, apk1OnCache.getDexFiles().size());
        Assert.assertEquals(
                123,
                apk1OnCache
                        .getDexFiles()
                        .get("classes.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing")
                        .longValue());
        Assert.assertEquals(
                345,
                apk1OnCache
                        .getDexFiles()
                        .get("classes02.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing2")
                        .longValue());

        Assert.assertEquals(
                "APK: hash02 code of classes.dex".hashCode(),
                apk2OnCache.getDexFiles().get("classes.dex").getChecksum());
        Assert.assertEquals(2, apk2OnCache.getDexFiles().size());
        Assert.assertEquals(
                123,
                apk2OnCache
                        .getDexFiles()
                        .get("classes.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing")
                        .longValue());
        Assert.assertEquals(
                567,
                apk2OnCache
                        .getDexFiles()
                        .get("classes02.dex")
                        .getClasssesChecksum()
                        .get("com.example.Testing2")
                        .longValue());
    }

    @Test
    public void testTwelveApksCache() throws Exception {
        DexArchiveDatabase db = getNewTestDb();
        for (int i = 0; i < 10; i++) {
            String hash = "hash" + i;

            // Create fake apk and cache it.
            Map<String, DexFile> dexfiles = makeDex(hash, "classes.dex");
            ((FakeDexFile) dexfiles.get("classes.dex")).addClass("com.example.Testing", 123 * i);
            DexArchive apkOnDisk = new DexArchive(hash, dexfiles);
            apkOnDisk.cache(db);

            // Retrieve the apk and verify.
            DexArchive apkOnCache = db.retrieveCache(hash);
            Assert.assertEquals(
                    ("APK: hash" + i + " code of classes.dex").hashCode(),
                    apkOnCache.getDexFiles().get("classes.dex").getChecksum());
            Assert.assertEquals(1, apkOnCache.getDexFiles().size());
            Assert.assertEquals(
                    123 * i,
                    apkOnCache
                            .getDexFiles()
                            .get("classes.dex")
                            .getClasssesChecksum()
                            .get("com.example.Testing")
                            .longValue());
        }
    }

    private Map<String, DexFile> makeDex(String apkHash, String... files) {
        Map<String, DexFile> result = new LinkedHashMap<>();
        for (String file : files) {
            result.put(file, new FakeDexFile(file, "APK: " + apkHash + " code of " + file));
        }
        return result;
    }
}
