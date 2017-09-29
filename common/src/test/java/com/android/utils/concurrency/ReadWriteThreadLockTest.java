/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.utils.concurrency;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.testutils.classloader.SingleClassLoader;
import com.android.testutils.concurrency.ConcurrencyTester;
import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Test;

/** Test cases for {@link ReadWriteThreadLock}. */
public class ReadWriteThreadLockTest {

    /** Type of lock. */
    private enum LockType {
        READ,
        WRITE
    }

    /** Type of lock method. */
    private enum LockMethod {
        LOCK,
        TRY_LOCK
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLockObjectClassLoadedOnlyOnce() throws Exception {
        // Create a lock based on a lock object whose class is loaded by the default class loader
        ReadWriteThreadLock lock1 = new ReadWriteThreadLock(new Foo());

        // Create a new lock from the same class and the same class loader, expect success
        ReadWriteThreadLock lock2 = new ReadWriteThreadLock(new Foo());
        assertThat(lock2).isNotSameAs(lock1);

        // Create a new lock from the same class but a different class loader, expect failure
        SingleClassLoader fooClassLoader = new SingleClassLoader(Foo.class.getName());
        Class fooClass = fooClassLoader.load();

        Constructor fooConstructor = fooClass.getDeclaredConstructor();
        fooConstructor.setAccessible(true);
        Object fooObject = fooConstructor.newInstance();

        try {
            //noinspection ResultOfObjectAllocationIgnored
            new ReadWriteThreadLock(fooObject);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("must be loaded once but is loaded twice");
        }

        // Create a new lock from a different class and a different class loader, expect success
        SingleClassLoader barClassLoader = new SingleClassLoader(Bar.class.getName());
        Class barClass = barClassLoader.load();

        Constructor barConstructor = barClass.getDeclaredConstructor();
        barConstructor.setAccessible(true);
        Object barObject = barConstructor.newInstance();

        //noinspection ResultOfObjectAllocationIgnored
        new ReadWriteThreadLock(barObject);
        assertThat(barObject).isNotSameAs(lock1);
    }

    /** Dummy class used by {@link #testLockObjectClassLoadedOnlyOnce}. */
    private static class Foo {}

    /** Dummy class used by {@link #testLockObjectClassLoadedOnlyOnce}. */
    private static class Bar {}

    @Test
    public void testLockAndTryLock() throws Exception {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(1);
        ReadWriteThreadLock.Lock writeLock = readWriteThreadLock.writeLock();
        ReadWriteThreadLock.Lock readLock = readWriteThreadLock.readLock();

        // Test both lock() and tryLock()
        readLock.lock();
        assertThat(tryLockInNewThread(readLock, true)).isTrue();
        assertThat(tryLockInNewThread(writeLock, true)).isFalse();
        readLock.unlock();

        writeLock.lock();
        assertThat(tryLockInNewThread(readLock, true)).isFalse();
        assertThat(tryLockInNewThread(writeLock, true)).isFalse();
        writeLock.unlock();

        // Test only tryLock()
        assertThat(tryLockInCurrentThread(readLock, false)).isTrue();
        assertThat(tryLockInNewThread(readLock, true)).isTrue();
        assertThat(tryLockInNewThread(writeLock, true)).isFalse();

        readLock.unlock();
        assertThat(tryLockInNewThread(readLock, true)).isTrue();
        assertThat(tryLockInNewThread(writeLock, true)).isTrue();

        assertThat(tryLockInCurrentThread(writeLock, false)).isTrue();
        assertThat(tryLockInNewThread(readLock, true)).isFalse();
        assertThat(tryLockInNewThread(writeLock, true)).isFalse();

        writeLock.unlock();
        assertThat(tryLockInNewThread(readLock, true)).isTrue();
        assertThat(tryLockInNewThread(writeLock, true)).isTrue();
    }

    private static boolean tryLockInCurrentThread(
            @NonNull ReadWriteThreadLock.Lock lock, boolean withUnlock) {
        boolean lockAcquired = lock.tryLock(1, TimeUnit.MILLISECONDS);
        if (lockAcquired && withUnlock) {
            lock.unlock();
        }
        return lockAcquired;
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean tryLockInNewThread(
            @NonNull ReadWriteThreadLock.Lock lock, boolean withUnlock) {
        try {
            return CompletableFuture.supplyAsync(() -> tryLockInCurrentThread(lock, withUnlock))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLock_ReadAndWriteLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.LOCK,
                tester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        tester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testLock_ReadLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                LockMethod.LOCK,
                tester);

        // Since we are using read locks, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testLock_DifferentLockObjects() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 2, 3},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.LOCK,
                tester);

        // Since we are using different lock files, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testTryLock_ReadAndWriteLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.TRY_LOCK,
                tester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        tester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testTryLock_ReadLocksOnSameLockObject() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 1, 1},
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                LockMethod.TRY_LOCK,
                tester);

        // Since we are using read locks, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testTryLock_DifferentLockObjects() throws IOException {
        ConcurrencyTester<Void, Void> tester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new Integer[] {1, 2, 3},
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.TRY_LOCK,
                tester);

        // Since we are using different lock files, the actions are allowed to run concurrently
        tester.assertThatActionsCanRunConcurrently();
    }

    /** Performs a few steps common to the concurrency tests. */
    private static void prepareConcurrencyTest(
            @NonNull Object[] lockObjects,
            @NonNull LockType[] lockTypes,
            @NonNull LockMethod lockMethod,
            @NonNull ConcurrencyTester<Void, Void> concurrencyTester) {
        for (int i = 0; i < lockObjects.length; i++) {
            Object lockObject = lockObjects[i];
            LockType lockType = lockTypes[i];

            concurrencyTester.addMethodInvocationFromNewThread(
                    (Function<Void, Void> instrumentedActionUnderTest) ->
                            executeActionWithLock(
                                    () -> instrumentedActionUnderTest.apply(null),
                                    lockObject,
                                    lockType,
                                    lockMethod),
                    getSampleAction());
        }
    }

    /** Returns a sample action. */
    private static Function<Void, Void> getSampleAction() {
        return (Void arg) -> {
            // Do some artificial work here
            assertThat(1).isEqualTo(1);
            return null;
        };
    }

    /** Executes an action after acquiring a lock and releases it when done. */
    private static void executeActionWithLock(
            @NonNull Runnable action,
            @NonNull Object lockObject,
            @NonNull LockType lockType,
            @NonNull LockMethod lockMethod) {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(lockObject);
        ReadWriteThreadLock.Lock lock =
                lockType == LockType.READ
                        ? readWriteThreadLock.readLock()
                        : readWriteThreadLock.writeLock();

        if (lockMethod == ReadWriteThreadLockTest.LockMethod.LOCK) {
            lock.lock();
        } else if (lockMethod == ReadWriteThreadLockTest.LockMethod.TRY_LOCK) {
            //noinspection StatementWithEmptyBody - Intentional
            while (!lock.tryLock(1, TimeUnit.MILLISECONDS)) ;
        } else {
            throw new AssertionError(lockMethod + " is not supported");
        }

        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testTryLock_Timeout() throws InterruptedException {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(1);
        ReadWriteThreadLock.Lock readLock = readWriteThreadLock.readLock();
        ReadWriteThreadLock.Lock writeLock = readWriteThreadLock.writeLock();

        // Let a new thread acquire a read lock
        CountDownLatch threadAcquiredLockEvent = new CountDownLatch(1);
        CountDownLatch threadCanResumeEvent = new CountDownLatch(1);
        new Thread(
                        () -> {
                            readLock.lock();
                            try {
                                threadAcquiredLockEvent.countDown();
                                try {
                                    threadCanResumeEvent.await();
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } finally {
                                readLock.unlock();
                            }
                        })
                .start();
        threadAcquiredLockEvent.await();

        // Check that if the lock can be acquired immediately, then even if the timeout is really
        // small or non-positive, it will still be acquired (since tryLock() attempts acquiring the
        // lock at least once before checking for timeout)
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertThat(readLock.tryLock(-1, TimeUnit.NANOSECONDS)).isTrue();
        readLock.unlock();
        assertThat(readLock.tryLock(0, TimeUnit.NANOSECONDS)).isTrue();
        readLock.unlock();
        assertThat(readLock.tryLock(1, TimeUnit.NANOSECONDS)).isTrue();
        readLock.unlock();

        // Also check that it doesn't take too long to acquire the lock
        stopwatch.stop();
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isLessThan((long) 500);

        // If the lock cannot be acquired, then it will return roughly when the timeout expires, not
        // sooner than the timeout and not too late after that
        stopwatch.reset();
        stopwatch.start();
        assertThat(writeLock.tryLock(500, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isAtLeast((long) 500);
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isAtMost((long) 1000);

        // Let the thread finish
        threadCanResumeEvent.countDown();
    }

    @Test
    public void testReentrantProperty() {
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(1);
        ReadWriteThreadLock.Lock readLock = readWriteThreadLock.readLock();
        ReadWriteThreadLock.Lock writeLock = readWriteThreadLock.writeLock();

        readLock.lock();
        try {
            readLock.lock();
            try {
                assertThat("This statement can run").isNotEmpty();
            } finally {
                readLock.unlock();
            }

            // Lock upgrade is not possible (the current thread will block forever), so we test with
            // tryLock() instead
            assertThat(writeLock.tryLock(1, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            writeLock.lock();
            try {
                assertThat("This statement can run").isNotEmpty();
            } finally {
                writeLock.unlock();
            }

            readLock.lock();
            try {
                assertThat("This statement can run").isNotEmpty();
            } finally {
                readLock.unlock();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDifferentClassLoaders() throws Exception {
        /*
         * Lock from a different class loader.
         */
        SingleClassLoader classLoader = new SingleClassLoader(ReadWriteThreadLock.class.getName());
        Class readWriteThreadLockClass = classLoader.load();

        Constructor constructor = readWriteThreadLockClass.getConstructor(Object.class);
        Object readWriteProcessLockObject = constructor.newInstance(1);

        Method readLockMethod = readWriteThreadLockClass.getMethod("readLock");
        Object readLockObject = readLockMethod.invoke(readWriteProcessLockObject);

        Method lockMethod = readLockObject.getClass().getMethod("lock");
        lockMethod.setAccessible(true);
        lockMethod.invoke(readLockObject);

        /*
         * Now lock from the current class loader, check that locking takes effect across class
         * loaders.
         */
        ReadWriteThreadLock readWriteThreadLock = new ReadWriteThreadLock(1);
        readWriteThreadLock.readLock().lock();
        try {
            assertThat("This statement can run").isNotEmpty();
        } finally {
            readWriteThreadLock.readLock().unlock();
        }
        assertThat(readWriteThreadLock.writeLock().tryLock(1, TimeUnit.MILLISECONDS)).isFalse();

        assertThat(tryLockInNewThread(readWriteThreadLock.readLock(), true)).isTrue();
        assertThat(tryLockInNewThread(readWriteThreadLock.writeLock(), true)).isFalse();

        readWriteThreadLock.readLock().unlock();
        assertThat(tryLockInCurrentThread(readWriteThreadLock.readLock(), true)).isTrue();
        assertThat(tryLockInNewThread(readWriteThreadLock.writeLock(), true)).isTrue();
    }
}
