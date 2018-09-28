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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

/**
 * A wrapper to an existing {@link DexArchiveDatabase} where every operation is done by a singled
 * threaded {@link ExecutorService}
 *
 * <p>In particular, this class provides an async implementatin of {@link
 * DexArchiveDatabase#enqueueUpdate(Function)}.
 *
 * <p>Because of the single threaded work queue nature of this class, it is also thread-safe.
 */
public class WorkQueueDexArchiveDatabase extends DexArchiveDatabase {
    private final DexArchiveDatabase delegate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * @param dexArchiveDatabase The database that this class delegates to do the actual data
     *     storage. This database needs not to be thread-safe.
     */
    public WorkQueueDexArchiveDatabase(DexArchiveDatabase dexArchiveDatabase) {
        delegate = dexArchiveDatabase;
    }

    @Override
    public synchronized void enqueueUpdate(Function<DexArchiveDatabase, Void> request) {
        FutureTask<DexArchiveDatabase> future =
                new FutureTask<DexArchiveDatabase>(
                        new Callable<DexArchiveDatabase>() {
                            @Override
                            public DexArchiveDatabase call() throws Exception {
                                request.apply(delegate);
                                return delegate;
                            }
                        });
        executor.execute(future);
    }

    private <R> R blockingRequest(Function<DexArchiveDatabase, R> request) {
        FutureTask<R> future = new FutureTask<>(() -> request.apply(delegate));
        executor.execute(future);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new DexArchiveDatabaseException(e);
        }
    }

    @Override
    public Map<String, Long> getClassesChecksum(int dexFileIndex) {
        return blockingRequest(db -> db.getClassesChecksum(dexFileIndex));
    }

    @Override
    public List<DexFileEntry> getDexFiles(String archiveChecksum) {
        return blockingRequest(db -> db.getDexFiles(archiveChecksum));
    }

    @Override
    public int getDexFileIndex(long dexFileChecksum) {
        return blockingRequest(db -> db.getDexFileIndex(dexFileChecksum));
    }

    @Override
    public int addDexFile(long dexFileChecksum, String name) {
        return blockingRequest(db -> db.addDexFile(dexFileChecksum, name));
    }

    @Override
    public void fillEntriesChecksum(int dexFileIndex, Map<String, Long> classesChecksums) {
        this.<Void>blockingRequest(
                db -> {
                    db.fillEntriesChecksum(dexFileIndex, classesChecksums);
                    return null;
                });
    }

    @Override
    public void fillDexFileList(String archiveChecksum, List<Integer> dexFilesIndex) {
        this.<Void>blockingRequest(
                db -> {
                    db.fillDexFileList(archiveChecksum, dexFilesIndex);
                    return null;
                });
    }

    @Override
    public void close() {
        this.<Void>blockingRequest(
                db -> {
                    db.close();
                    return null;
                });
    }
}
