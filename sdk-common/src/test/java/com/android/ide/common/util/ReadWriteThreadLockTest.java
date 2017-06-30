/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ide.common.util;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.classloader.SingleClassLoader;
import com.android.testutils.concurrency.ConcurrencyTester;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link ReadWriteThreadLock}. */
public class ReadWriteThreadLockTest {

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    /** Type of lock. */
    private enum LockType {
        READ,
        WRITE
    }

    @Test
    public void testReadAndWriteLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                tester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        tester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testReadLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                tester);

        // Since we are using read locks, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testDifferentLockObjects() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 2, 3},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                tester);

        // Since we are using different lock files, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    /** Performs a few steps common to the concurrency tests. */
    private static void prepareConcurrencyTest(
            @NonNull Object[] lockObjects,
            @NonNull LockType[] lockTypes,
            @NonNull ConcurrencyTester<Void, Void> concurrencyTester) {
        Function<Void, Void> actionUnderTest = (Void arg) -> {
            // Do some artificial work here
            assertThat(1).isEqualTo(1);
            return null;
        };
        for (int i = 0; i < lockObjects.length; i++) {
            Object lockObject = lockObjects[i];
            LockType lockType = lockTypes[i];

            concurrencyTester.addMethodInvocationFromNewThread(
                    (Function<Void, Void> anActionUnderTest) -> {
                        try {
                            executeActionWithLock(
                                    () -> anActionUnderTest.apply(null), lockObject, lockType);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    actionUnderTest);
        }
    }

    /** Executes an action with a lock. */
    private static void executeActionWithLock(
            @NonNull Runnable action,
            @NonNull Object lockObject,
            @NonNull LockType lockType)
            throws IOException {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(lockObject);
        ReadWriteThreadLock.Lock lock =
                lockType == LockType.READ
                        ? readWriteThreadLock.readLock()
                        : readWriteThreadLock.writeLock();
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testReentrantProperty() {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(1);
        ReadWriteThreadLock.Lock readLock = readWriteThreadLock.readLock();
        ReadWriteThreadLock.Lock writeLock = readWriteThreadLock.writeLock();
        writeLock.lock();
        try {
            writeLock.lock();
            try {
                readLock.lock();
                try {
                    readLock.lock();
                    try {
                        assertThat("This statement can run").isNotEmpty();
                    } finally {
                        readLock.unlock();
                    }
                } finally {
                    readLock.unlock();
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDifferentClassLoaders() throws Exception {
        ReadWriteThreadLock originalLock = new ReadWriteThreadLock(1);
        originalLock.writeLock().lock();

        // If another thread attempts to acquire the same write lock, it must not succeed
        ReadWriteThreadLock lockWithSameClassLoader = new ReadWriteThreadLock(1);
        Thread thread1 = new Thread(() -> {
            lockWithSameClassLoader.writeLock().lock();
            lockWithSameClassLoader.writeLock().unlock();
        });
        thread1.start();
        thread1.join(100);
        assertThat(thread1.isAlive()).isTrue();

        // Allow the thread to finish
        originalLock.writeLock().unlock();
        thread1.join();

        // Acquire the write lock again
        originalLock.writeLock().lock();

        // If another thread attempts to acquire the same write lock, it must not succeed, even when
        // the ReadWriteThreadLock class is loaded by a different class loader
        SingleClassLoader classLoader = new SingleClassLoader(ReadWriteThreadLock.class.getName());
        Class clazz = classLoader.load();
        Constructor constructor = clazz.getConstructor(Object.class);
        constructor.setAccessible(true);
        Object lockWithDifferentClassLoader = constructor.newInstance(1);
        Method writeLockMethod = clazz.getMethod("writeLock");
        Object lock = writeLockMethod.invoke(lockWithDifferentClassLoader);
        Method lockMethod = lock.getClass().getMethod("lock");
        Method unlockMethod = lock.getClass().getMethod("unlock");
        lockMethod.setAccessible(true);
        unlockMethod.setAccessible(true);
        Thread thread2 = new Thread(() -> {
            try {
                lockMethod.invoke(lock);
                unlockMethod.invoke(lock);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
        thread2.start();
        thread2.join(100);
        assertThat(thread2.isAlive()).isTrue();

        // Allow the thread to finish
        originalLock.writeLock().unlock();
        thread2.join();
    }
}
