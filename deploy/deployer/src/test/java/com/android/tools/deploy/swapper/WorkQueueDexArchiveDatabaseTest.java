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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit tests for {@link WorkQueueDexArchiveDatabase}.
 *
 * <p>The tests will be done with both {@link SQLiteDexArchiveDatabase} and {@link
 * InMemoryDexArchiveDatabase} as the backing database. Core database functionality will not be
 * tested here. Instead, the focus here will be the {@link WorkQueueDexArchiveDatabase}'s ability to
 * queue up a list of update and retrieve request in order.
 */
@RunWith(Parameterized.class)
public class WorkQueueDexArchiveDatabaseTest {

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
                    {() -> new InMemoryDexArchiveDatabase()},
                    {
                        () -> {
                            TemporaryFolder tmpdir = new TemporaryFolder();
                            tmpdir.create();
                            return new SQLiteDexArchiveDatabase(tmpdir.newFile("test.db"));
                        }
                    }
                });
    }

    /** Call to create a database of the current favor. */
    private WorkQueueDexArchiveDatabase getNewTestDb() throws Exception {
        return new WorkQueueDexArchiveDatabase(dbCreator.create());
    }

    @Test
    public void testSimpleOperations() throws Exception {
        WorkQueueDexArchiveDatabase db = getNewTestDb();
        int id = db.addDexFile(111, "classes01.dex");
        assertEquals(id, db.getDexFileIndex(111));
    }

    @Test
    public void testAsyncUpdate() throws Exception {
        WorkQueueDexArchiveDatabase db = getNewTestDb();
        Object lock = new Object();
        AtomicInteger seqNumber = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(-100);
        seqNumber.set(1);

        db.enqueueUpdate(
                database -> {
                    waitForSequence(lock, seqNumber, 2);

                    // We will let the main thread continue to run, however, since it is doing a synchronized call to the db, it will block until we are
                    // done here.
                    notifySequence(lock, seqNumber, 3);

                    // This happens right the way as we shall demonstrate.
                    int id = database.addDexFile(111, "classes01.dex");
                    assertEquals(id, database.getDexFileIndex(111));

                    index.set(id);
                    return null;
                });

        notifySequence(lock, seqNumber, 2);
        waitForSequence(lock, seqNumber, 3);
        int id = db.getDexFileIndex(111);
        assertEquals(index.get(), id);
    }

    @Test
    public void testDoubleAsyncUpdate() throws Exception {
        WorkQueueDexArchiveDatabase db = getNewTestDb();
        Object lock = new Object();
        AtomicInteger seqNumber = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(-100);
        seqNumber.set(1);

        db.enqueueUpdate(
                database -> {
                    waitForSequence(lock, seqNumber, 2);
                    notifySequence(lock, seqNumber, 3);

                    // Even thought the 2nd update is queued up, the data base should NOT be updated until this function is done.
                    assertEquals(-1, database.getDexFileIndex(222));

                    int id = database.addDexFile(111, "classes01.dex");
                    assertEquals(id, database.getDexFileIndex(111));
                    System.out.println("setting 111");
                    index.set(id);
                    return null;
                });

        db.enqueueUpdate(
                database -> {
                    // Verify the previous update is completed.
                    assertEquals(index.get(), database.getDexFileIndex(111));
                    int id = database.addDexFile(222, "classes01.dex");
                    assertEquals(id, database.getDexFileIndex(222));
                    index.set(id);
                    return null;
                });

        notifySequence(lock, seqNumber, 2);
        waitForSequence(lock, seqNumber, 3);

        int id = db.getDexFileIndex(222);
        assertEquals(index.get(), id);
    }

    /** Block the current thread until the sequence number reaches certain value; */
    private static void waitForSequence(Object lock, AtomicInteger seqNumber, int value) {
        while (seqNumber.get() != value) {
            synchronized (lock) {
                try {
                    lock.wait(10);
                } catch (InterruptedException e) {
                    Assert.fail();
                }
            }
        }
    }

    /** Change the sequence value and notify all other threads that are blocked on it. */
    private static void notifySequence(Object lock, AtomicInteger seqNumber, int value) {
        seqNumber.set(value);
        synchronized (lock) {
            lock.notifyAll();
        }
    }
}
