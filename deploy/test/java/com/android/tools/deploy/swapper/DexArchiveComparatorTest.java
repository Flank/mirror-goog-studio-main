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

import java.io.*;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DexArchiveComparatorTest {

    /**
     * This test simulate a very basic hotswap session.
     *
     * <p>APK1 represents a previous built and APK2 is a brand new built that is meant to be
     * hotswapped. APK1 is first read and cached into the database prior getting compared to emulate
     * a build system scenerio.
     *
     * <p>The end result {@link DexArchiveComparator.Entry} would normally be then piped to one of
     * the {@link ClassRedefiner} implementation for hot swapping.
     */
    @Test
    public void testDiffApk() throws Exception {
        InMemoryDexArchiveDatabase db = new InMemoryDexArchiveDatabase();
        String apk1Location = getProcessPath("apk1.location");
        String apk2Location = getProcessPath("apk2.location");

        // Read the old APK First.
        String apk1Checksum = "DEADBEEF";
        DexArchive apk1 =
                DexArchive.buildFromHostFileSystem(new ZipFile(apk1Location), apk1Checksum);

        // Cache the APK in the in memory database.
        apk1.cache(db);
        DexArchive apk1Cache = DexArchive.buildFromDatabase(db, apk1Checksum);

        // Test a disk write
        TemporaryFolder tmpDir = new TemporaryFolder();
        tmpDir.create();
        File dbSerialized = tmpDir.newFile("InMemoryDb.serialized");
        ObjectOutputStream outputStream =
                new ObjectOutputStream(new FileOutputStream(dbSerialized));
        outputStream.writeObject(db);
        outputStream.close();

        ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(dbSerialized));
        db = (InMemoryDexArchiveDatabase) inputStream.readObject();
        inputStream.close();

        // Run some basic asserts on the database.
        // TODO: May be move this to a seperate tests?
        List<DexArchiveDatabase.DexFileEntry> cachedOldDexFiles = db.getDexFiles(apk1Checksum);
        Assert.assertNotNull(cachedOldDexFiles);
        Assert.assertEquals(1, cachedOldDexFiles.size());
        DexArchiveDatabase.DexFileEntry oldDexFile = cachedOldDexFiles.get(0);
        Assert.assertEquals("classes.dex", oldDexFile.name);

        // Read the new APK
        String apk2Checksum = "BEEFDEAD";
        DexArchive apk2 =
                DexArchive.buildFromHostFileSystem(new ZipFile(apk2Location), apk2Checksum);

        // Verify the delta.
        DexArchiveComparator.Result result = new DexArchiveComparator().compare(apk1Cache, apk2);
        Assert.assertEquals(1, result.changedClasses.size());
        DexArchiveComparator.Entry changed = result.changedClasses.get(0);
        Assert.assertEquals("testapk.Changed", changed.name);
        Assert.assertNotNull(changed.dex);

        // Also cache the new APK
        apk2.cache(db);
        // Run more basic asserts on the database.
        // TODO: May be move this to a seperate tests?
        List<DexArchiveDatabase.DexFileEntry> cachedNewDexFiles = db.getDexFiles(apk2Checksum);
        Assert.assertNotNull(cachedNewDexFiles);
        Assert.assertEquals(1, cachedNewDexFiles.size());
        DexArchiveDatabase.DexFileEntry newDexFile = cachedNewDexFiles.get(0);
        Assert.assertEquals("classes.dex", newDexFile.name);
        // The checksum should not match for the dex file that is different.
        Assert.assertNotEquals(oldDexFile.checksum, newDexFile.checksum);
    }

    public static String getProcessPath(String property) {
        return System.getProperty("user.dir") + File.separator + System.getProperty(property);
    }
}
