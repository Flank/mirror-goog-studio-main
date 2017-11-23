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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.testutils.classloader.SingleClassLoader;
import com.android.testutils.concurrency.ConcurrencyTester;
import com.android.testutils.concurrency.InterProcessConcurrencyTester;
import com.android.utils.FileUtils;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test cases for {@link ReadWriteProcessLock}. */
public class ReadWriteProcessLockTest {

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

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

    @Test
    public void testLockFileBeforeAndAfter() throws IOException {
        // Case 1: lock file already existed, and is not deleted
        File lockFile1 = testFolder.newFile("lockfile1");
        assertThat(lockFile1).isFile();

        ReadWriteProcessLock readWriteLock1 = new ReadWriteProcessLock(lockFile1.toPath());
        ReadWriteProcessLock.Lock lock1 = readWriteLock1.readLock();
        assertThat(lockFile1).isFile();

        lock1.lock();
        assertThat(lockFile1).isFile();

        lock1.unlock();
        assertThat(lockFile1).isFile();

        // Case 2: lock file did not exist, is created and not deleted
        File lockFile3 = new File(testFolder.getRoot(), "lockfile3");
        assertThat(lockFile3).doesNotExist();

        ReadWriteProcessLock readWriteLock3 = new ReadWriteProcessLock(lockFile3.toPath());
        ReadWriteProcessLock.Lock lock3 = readWriteLock3.writeLock();
        assertThat(lockFile3).isFile();

        lock3.lock();
        assertThat(lockFile3).isFile();

        lock3.unlock();
        assertThat(lockFile3).isFile();
    }

    @Test
    public void testLockFile_ParentDirectoryDoesNotExist() throws Exception {
        File lockFile = FileUtils.join(testFolder.getRoot(), "dir", "lockfile");
        try {
            //noinspection ResultOfObjectAllocationIgnored
            new ReadWriteProcessLock(lockFile.toPath());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("does not exist");
        }
    }

    @Test
    public void testCreateAndNormalizeLockFile() throws Exception {
        File lockFile = new File(testFolder.getRoot(), "lockfile");

        // Check that lock file is created if it does not yet exist
        assertThat(lockFile).doesNotExist();
        ReadWriteProcessLock.createAndNormalizeLockFile(lockFile.toPath());
        assertThat(lockFile).exists();

        // Check that lock file is not deleted if it already exists
        assertThat(lockFile).exists();
        ReadWriteProcessLock.createAndNormalizeLockFile(lockFile.toPath());
        assertThat(lockFile).exists();

        // Check that an exception is thrown if the lock file's parent directory does not yet exist
        File lockFile2 = FileUtils.join(testFolder.getRoot(), "dir", "lockfile");
        assertThat(lockFile2.getParentFile()).doesNotExist();
        try {
            ReadWriteProcessLock.createAndNormalizeLockFile(lockFile2.toPath());
            fail("Expect IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e)
                    .hasMessage(
                            "Parent directory of "
                                    + lockFile2.getAbsolutePath()
                                    + " does not exist");
        }

        // Check that the lock file's path is normalized
        lockFile = FileUtils.join(testFolder.getRoot(), "dir", "..", "lockfile");
        File normalizedLockFile =
                ReadWriteProcessLock.createAndNormalizeLockFile(lockFile.toPath()).toFile();
        assertThat(normalizedLockFile).exists();
        assertThat(normalizedLockFile).isNotEqualTo(lockFile);
        assertThat(normalizedLockFile).isEqualTo(lockFile.getCanonicalFile());

        // Check that an exception is thrown if a directory with the same path as the lock file
        // accidentally exists
        lockFile = testFolder.newFolder();
        assertThat(lockFile).isDirectory();
        try {
            ReadWriteProcessLock.createAndNormalizeLockFile(lockFile.toPath());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("is a directory");
        }

        // Check that an exception is thrown if a regular (non-empty) file with the same path as the
        // lock file accidentally exists
        lockFile = testFolder.newFile();
        Files.write(new byte[] {1}, lockFile);
        try {
            ReadWriteProcessLock.createAndNormalizeLockFile(lockFile.toPath());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("cannot be used as a lock file");
        }
    }

    @Test
    public void testLockAndTryLock() throws Exception {
        File lockFile = testFolder.newFile("lockfile");
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock writeLock = readWriteProcessLock.writeLock();
        ReadWriteProcessLock.Lock readLock = readWriteProcessLock.readLock();

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
            @NonNull ReadWriteProcessLock.Lock lock, boolean withUnlock) {
        try {
            boolean lockAcquired = lock.tryLock(1, TimeUnit.MILLISECONDS);
            if (lockAcquired && withUnlock) {
                lock.unlock();
            }
            return lockAcquired;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean tryLockInNewThread(
            @NonNull ReadWriteProcessLock.Lock lock, boolean withUnlock) {
        try {
            return CompletableFuture.supplyAsync(() -> tryLockInCurrentThread(lock, withUnlock))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLock_ReadAndWriteLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        interProcessTester.assertThatActionsCannotRunConcurrently();
        singleProcessTester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testLock_ReadLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                LockMethod.LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using read locks, the actions are allowed to run concurrently
        interProcessTester.assertThatActionsCanRunConcurrently();
        singleProcessTester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testLock_DifferentLockFiles() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile1"),
                    new File(testFolder.getRoot(), "lockfile2"),
                    new File(testFolder.getRoot(), "lockfile3")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using different lock files, the actions are allowed to run concurrently
        interProcessTester.assertThatActionsCanRunConcurrently();
        singleProcessTester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testTryLock_ReadAndWriteLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.TRY_LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        interProcessTester.assertThatActionsCannotRunConcurrently();
        singleProcessTester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testTryLock_ReadLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                LockMethod.TRY_LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using read locks, the actions are allowed to run concurrently
        interProcessTester.assertThatActionsCanRunConcurrently();
        singleProcessTester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testTryLock_DifferentLockFiles() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile1"),
                    new File(testFolder.getRoot(), "lockfile2"),
                    new File(testFolder.getRoot(), "lockfile3")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                LockMethod.TRY_LOCK,
                interProcessTester,
                singleProcessTester);

        // Since we are using different lock files, the actions are allowed to run concurrently
        interProcessTester.assertThatActionsCanRunConcurrently();
        singleProcessTester.assertThatActionsCanRunConcurrently();
    }

    /** Performs a few steps common to the concurrency tests. */
    private static void prepareConcurrencyTest(
            @NonNull File[] lockFiles,
            @NonNull LockType[] lockTypes,
            @NonNull LockMethod lockMethod,
            @NonNull InterProcessConcurrencyTester interProcessTester,
            @NonNull ConcurrencyTester<Void, Void> singleProcessTester) {
        for (int i = 0; i < lockFiles.length; i++) {
            File lockFile = lockFiles[i];
            LockType lockType = lockTypes[i];

            interProcessTester.addClassInvocationFromNewProcess(
                    ActionWithLock.class,
                    new String[] {lockFile.getPath(), lockType.toString(), lockMethod.name()});

            singleProcessTester.addMethodInvocationFromNewThread(
                    (Function<Void, Void> instrumentedActionUnderTest) -> {
                        try {
                            executeActionWithLock(
                                    () -> instrumentedActionUnderTest.apply(null),
                                    lockFile,
                                    lockType,
                                    lockMethod);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
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

    /**
     * Private class whose main() method executes a sample action after acquiring a lock and
     * releases it when done.
     */
    private static final class ActionWithLock {

        public static void main(String[] args) throws IOException {
            File lockFile = new File(args[0]);
            LockType lockType = LockType.valueOf(args[1]);
            LockMethod lockMethod = LockMethod.valueOf(args[2]);
            // The server socket port is added to the list of arguments by
            // InterProcessConcurrencyTester for the client process to communicate with the main
            // process
            int serverSocketPort = Integer.valueOf(args[3]);

            InterProcessConcurrencyTester.MainProcessNotifier notifier =
                    new InterProcessConcurrencyTester.MainProcessNotifier(serverSocketPort);
            notifier.processStarted();

            Function<Void, Void> instrumentedActionUnderTest =
                    (Void arg) -> {
                        try {
                            notifier.actionStarted();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        // Execute a sample action
                        return getSampleAction().apply(arg);
                    };

            executeActionWithLock(
                    () -> instrumentedActionUnderTest.apply(null), lockFile, lockType, lockMethod);
        }
    }

    /** Executes an action after acquiring a lock and releases it when done. */
    private static void executeActionWithLock(
            @NonNull Runnable action,
            @NonNull File lockFile,
            @NonNull LockType lockType,
            @NonNull LockMethod lockMethod)
            throws IOException {
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock lock =
                lockType == LockType.READ
                        ? readWriteProcessLock.readLock()
                        : readWriteProcessLock.writeLock();

        if (lockMethod == LockMethod.LOCK) {
            lock.lock();
        } else if (lockMethod == LockMethod.TRY_LOCK) {
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
    public void testTryLock_Timeout() throws Exception {
        File lockFile = testFolder.newFile("lockfile");
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock readLock = readWriteProcessLock.readLock();
        ReadWriteProcessLock.Lock writeLock = readWriteProcessLock.writeLock();

        // Let a new thread acquire a read lock
        CountDownLatch threadAcquiredLockEvent = new CountDownLatch(1);
        CountDownLatch threadCanResumeEvent = new CountDownLatch(1);
        new Thread(
                        () -> {
                            try {
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
                            } catch (IOException e) {
                                throw new RuntimeException(e);
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
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isLessThan((long) 1000);

        // If the lock cannot be acquired, then it will return roughly when the timeout expires, not
        // sooner than the timeout and not too late after that
        stopwatch.reset();
        stopwatch.start();
        assertThat(writeLock.tryLock(1000, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isAtLeast((long) 1000);
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS)).isAtMost((long) 2000);

        // Let the thread finish
        threadCanResumeEvent.countDown();
    }

    @Test
    public void testNonReentrantProperty() throws IOException {
        File lockFile = testFolder.newFile("lockfile");
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock readLock = readWriteProcessLock.readLock();
        ReadWriteProcessLock.Lock writeLock = readWriteProcessLock.writeLock();

        readLock.lock();
        try {
            try {
                readLock.lock();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
            }

            // Lock upgrade makes the current thread block forever, so we test with tryLock()
            // instead
            assertThat(writeLock.tryLock(1, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            try {
                writeLock.lock();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
            }

            try {
                readLock.lock();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
            }
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDifferentClassLoaders() throws Exception {
        File lockFile = testFolder.newFile("lockfile");

        /*
         * Lock from a different class loader.
         */
        SingleClassLoader classLoader = new SingleClassLoader(ReadWriteProcessLock.class.getName());
        Class readWriteProcessLockClass = classLoader.load();

        Constructor constructor = readWriteProcessLockClass.getConstructor(Path.class);
        Object readWriteProcessLockObject = constructor.newInstance(lockFile.toPath());

        Method readLockMethod = readWriteProcessLockClass.getMethod("readLock");
        Object readLockObject = readLockMethod.invoke(readWriteProcessLockObject);

        Method lockMethod = readLockObject.getClass().getMethod("lock");
        lockMethod.setAccessible(true);
        lockMethod.invoke(readLockObject);

        /*
         * Now lock from the current class loader, check that locking takes effect across class
         * loaders.
         */
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        try {
            readWriteProcessLock.readLock().lock();
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
        }
        assertThat(readWriteProcessLock.writeLock().tryLock(1, TimeUnit.MILLISECONDS)).isFalse();

        assertThat(tryLockInNewThread(readWriteProcessLock.readLock(), true)).isTrue();
        assertThat(tryLockInNewThread(readWriteProcessLock.writeLock(), true)).isFalse();

        readWriteProcessLock.readLock().unlock();
        assertThat(tryLockInCurrentThread(readWriteProcessLock.readLock(), true)).isTrue();
        assertThat(tryLockInNewThread(readWriteProcessLock.writeLock(), true)).isTrue();
    }
}
