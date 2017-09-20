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
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.After;
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

    @After
    public void tearDown() throws IOException {
        FileUtils.deletePath(testFolder.getRoot());
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
        assertThat(lockFile).doesNotExist();
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
    public void testReadAndWriteLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
                interProcessTester,
                singleProcessTester);

        // Since we are using read and write locks, the actions are not allowed to run concurrently
        interProcessTester.assertThatActionsCannotRunConcurrently();
        singleProcessTester.assertThatActionsCannotRunConcurrently();
    }

    @Test
    public void testReadLocksOnSameLockFile() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile"),
                    new File(testFolder.getRoot(), "lockfile")
                },
                new LockType[] {LockType.READ, LockType.READ, LockType.READ},
                interProcessTester,
                singleProcessTester);

        // Since we are using read locks, the actions are allowed to run concurrently
        interProcessTester.assertThatActionsCanRunConcurrently();
        singleProcessTester.assertThatActionsCanRunConcurrently();
    }

    @Test
    public void testDifferentLockFiles() throws IOException {
        InterProcessConcurrencyTester interProcessTester = new InterProcessConcurrencyTester();
        ConcurrencyTester<Void, Void> singleProcessTester = new ConcurrencyTester<>();

        prepareConcurrencyTest(
                new File[] {
                    new File(testFolder.getRoot(), "lockfile1"),
                    new File(testFolder.getRoot(), "lockfile2"),
                    new File(testFolder.getRoot(), "lockfile3")
                },
                new LockType[] {LockType.READ, LockType.WRITE, LockType.WRITE},
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
            @NonNull InterProcessConcurrencyTester interProcessTester,
            @NonNull ConcurrencyTester<Void, Void> singleProcessTester) {
        Function<Void, Void> actionUnderTest = (Void arg) -> {
            // Do some artificial work here
            assertThat(1).isEqualTo(1);
            return null;
        };
        for (int i = 0; i < lockFiles.length; i++) {
            File lockFile = lockFiles[i];
            LockType lockType = lockTypes[i];

            interProcessTester.addClassInvocationFromNewProcess(
                    SampleAction.class, new String[] {lockFile.getPath(), lockType.toString()});

            singleProcessTester.addMethodInvocationFromNewThread(
                    (Function<Void, Void> anActionUnderTest) -> {
                        try {
                            executeActionWithLock(
                                    () -> anActionUnderTest.apply(null), lockFile, lockType);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    actionUnderTest);
        }
    }

    /** Private class whose main() method will execute a sample action. */
    private static final class SampleAction {

        public static void main(String[] args) throws IOException {
            File lockFile = new File(args[0]);
            LockType lockType = LockType.valueOf(args[1]);
            // The server socket port is added to the list of arguments by
            // InterProcessConcurrencyTester for the client process to communicate with the main
            // process
            int serverSocketPort = Integer.valueOf(args[2]);

            InterProcessConcurrencyTester.MainProcessNotifier notifier =
                    new InterProcessConcurrencyTester.MainProcessNotifier(serverSocketPort);
            notifier.processStarted();

            Runnable actionUnderTest = () -> {
                try {
                    notifier.actionStarted();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // Do some artificial work here
                assertThat(1).isEqualTo(1);
            };

            executeActionWithLock(actionUnderTest, lockFile, lockType);
        }
    }

    /** Executes an action with a lock. */
    private static void executeActionWithLock(
            @NonNull Runnable action,
            @NonNull File lockFile,
            @NonNull LockType lockType)
            throws IOException {
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock lock =
                lockType == LockType.READ
                        ? readWriteProcessLock.readLock()
                        : readWriteProcessLock.writeLock();
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testNonReentrantProperty() throws IOException {
        File lockFile = testFolder.newFile("lockfile");
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
        ReadWriteProcessLock.Lock readLock = readWriteProcessLock.readLock();
        ReadWriteProcessLock.Lock writeLock = readWriteProcessLock.writeLock();

        writeLock.lock();
        try {
            try {
                readLock.lock();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
            }
            try {
                writeLock.lock();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).contains("ReadWriteProcessLock is not reentrant");
            }
        } finally {
            writeLock.unlock();
        }

        readLock.lock();
        try {
            readLock.lock();
            try {
                assertThat("It's okay to acquire a read lock twice").isNotEmpty();
            } finally {
                readLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    @Test
    public void testDifferentClassLoaders() throws Exception {
        File lockFile = testFolder.newFile("lockfile");

        ReadWriteProcessLock originalLock = new ReadWriteProcessLock(lockFile.toPath());
        originalLock.readLock().lock();

        // Check that it's okay to acquire the same read lock again (without any
        // OverlappingFileLockException)
        ReadWriteProcessLock lockWithSameClassLoader = new ReadWriteProcessLock(lockFile.toPath());
        lockWithSameClassLoader.readLock().lock();

        // Check that it's okay to acquire the same read lock again (without any
        // OverlappingFileLockException) even when the ReadWriteProcessLock class is loaded by a
        // different class loader
        SingleClassLoader classLoader = new SingleClassLoader(ReadWriteProcessLock.class.getName());
        Class clazz = classLoader.load();

        @SuppressWarnings("unchecked")
        Constructor constructor = clazz.getConstructor(Path.class);
        constructor.setAccessible(true);
        Object lockWithDifferentClassLoader = constructor.newInstance(lockFile.toPath());

        @SuppressWarnings("unchecked")
        Method readLockMethod = clazz.getMethod("readLock");
        Object lock = readLockMethod.invoke(lockWithDifferentClassLoader);
        Method lockMethod = lock.getClass().getMethod("lock");

        lockMethod.setAccessible(true);
        lockMethod.invoke(lock);
    }
}
