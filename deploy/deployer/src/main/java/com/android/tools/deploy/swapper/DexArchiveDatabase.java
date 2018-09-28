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

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A database to hold the dex content information of a {@link OnHostDexFile}. They can be retrieved
 * as a {@link OnCacheDexFile} later for comparisons.
 *
 * <p>This class provides the high level abstraction that interacts with the {@link DexArchive} API.
 * Subclass of this class should provide more low level implementation that can be easily
 * implemented with relational database.
 */
public abstract class DexArchiveDatabase {

    public DexArchive retrieveCache(String checksum) {
        Map<String, DexFile> dexFiles = new HashMap<>();

        List<DexFileEntry> dexFileEntries = this.getDexFiles(checksum);
        if (dexFileEntries == null) {
            return null;
        }
        for (DexFileEntry entry : dexFileEntries) {
            dexFiles.put(
                    entry.name,
                    new OnCacheDexFile(
                            entry.checksum, entry.name, getDexFileIndex(entry.checksum), this));
        }

        return new DexArchive(checksum, dexFiles);
    }

    protected static class DexFileEntry implements Serializable {
        public final int index;
        public final long checksum;
        public final String name;

        public DexFileEntry(int index, long checksum, String name) {
            this.index = index;
            this.checksum = checksum;
            this.name = name;
        }
    }

    // Low level implementations below.

    /** Invoked when we want to terminate connection to the database */
    public void close() {}

    /** Returns Classname -> Checksum Map of the given dex file index. */
    public abstract Map<String, Long> getClassesChecksum(int dexFileIndex);

    /** List all the (index, checksum, name) of the dex files of a given archive. */
    public abstract List<DexFileEntry> getDexFiles(String archiveChecksum);

    /** Returns an the dex file's index from the table given the checksum of a dex file. */
    public abstract int getDexFileIndex(long dexFileChecksum);

    /** Add a new dex file to database. */
    public abstract int addDexFile(long dexFileChecksum, String name);

    /**
     * Fill a dex file with a map of classname to checksum. The dex file needs to be added prior
     * since it requires an index to the table.
     */
    public abstract void fillEntriesChecksum(int dexFileIndex, Map<String, Long> classesChecksums);

    /**
     * Fill all the dex files within a newly added archive. Unlike fillEntriesChecksum, this does
     * not require an index since archives are not shared, just dex files.
     */
    public abstract void fillDexFileList(String archiveChecksum, List<Integer> dexFilesIndex);

    /**
     * Enqueues a lambda function that will perform some operations on the database asynchronously.
     *
     * <p>All future requests enqueued will not be performed until this request lambda returns.
     * Also, other operations available in the {@link DexArchiveDatabase} will block until this
     * lambda return.
     *
     * <p>Ideally request should be a function perform some expensive operations to compute
     * checksum. DO NOT use references to the {@link WorkQueueDexArchiveDatabase} to perform queries
     * as it will deadlock for obvious reasons. Instead, the provided database (db) is available for
     * direct update operations.
     *
     * @param request
     */
    public synchronized void enqueueUpdate(Function<DexArchiveDatabase, Void> request) {
        // By default, this is a blocking call. Implementation should provide this functionality.
        request.apply(this);
    }
}
