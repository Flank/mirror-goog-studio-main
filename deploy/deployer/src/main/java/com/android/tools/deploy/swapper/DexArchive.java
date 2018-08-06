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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An abstract representation of an Archive (.apk) that contains multiple DexFile files (.dex) that
 * holds Classes for hot swapping.
 *
 * <p>
 *
 * <p>Classes represented with a CRC32 checksum for quick comparison and extracting deltas between
 * builds.
 *
 * <p>
 *
 * <p>The API is designed to allow efficient storage in a database like cache for small incremental
 * changes. Ideally, APKs across incremental builds should have large number of identical dexFiles
 * (IE: classesXX.dex unchanged after build) and the DexArchive object as well as the serialized
 * version of that object in the database cache would share links to a single dex file entry in such
 * case.
 *
 * <p>
 *
 * <p>{@see DexArchiveDatabase}
 */
public final class DexArchive {

    /** Given a checksum, retrieve a representation of the archive from the database. */
    public static DexArchive buildFromDatabase(DexArchiveDatabase db, String checksum) {
        return db.retrieveCache(checksum);
    }

    /** Given a zip file, builds an representation of the archive. */
    public static DexArchive buildFromHostFileSystem(ZipInputStream zis, String checksum)
            throws IOException {
        final Map<String, DexFile> dexFiles = new HashMap<>();
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            if (!entry.getName().endsWith(".dex")) {
                zis.closeEntry();
                continue;
            }
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream dexContent = new ByteArrayOutputStream();
            int len;
            while ((len = zis.read(buffer)) > 0) {
                dexContent.write(buffer, 0, len);
            }
            byte[] code = dexContent.toByteArray();
            dexFiles.put(entry.getName(), new OnHostDexFile(entry.getCrc(), entry.getName(), code));
            zis.closeEntry();
        }
        zis.close();
        return new DexArchive(checksum, dexFiles);
    }

    private final String checksum;
    private final Map<String, DexFile> dexFiles;

    public DexArchive(String checksum, Map<String, DexFile> dexFiles) {
        this.checksum = checksum;
        this.dexFiles = dexFiles;
    }

    /** @return A map of filename to DexFile objects. */
    public Map<String, DexFile> getDexFiles() {
        return dexFiles;
    }

    /** Saves all the checksum within an archive to a cache. */
    public void cache(DexArchiveDatabase db) {
        Set<Map.Entry<String, DexFile>> entrySet = getDexFiles().entrySet();
        List<Integer> dexFilesIndex = new ArrayList<>(entrySet.size());
        for (Map.Entry<String, DexFile> e : entrySet) {
            dexFilesIndex.add(e.getValue().cache(db));
        }
        db.fillDexFileList(checksum, dexFilesIndex);
    }
}
