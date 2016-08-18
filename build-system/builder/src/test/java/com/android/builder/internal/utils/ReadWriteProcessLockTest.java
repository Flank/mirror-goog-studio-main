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

package com.android.builder.internal.utils;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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

    @Test
    public void testSameLockForSameLockFile() throws IOException {
        File lockFile = new File(testFolder.getRoot(), "lockfile");
        ReadWriteProcessLock lock1 = ReadWriteProcessLock.getInstance(lockFile);
        ReadWriteProcessLock lock2 = ReadWriteProcessLock.getInstance(lockFile);
        assertThat(lock1).isSameAs(lock2);
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
    private void prepareConcurrencyTest(
            @NonNull File[] lockFiles,
            @NonNull LockType[] lockTypes,
            @NonNull InterProcessConcurrencyTester interProcessTester,
            @NonNull ConcurrencyTester<Void, Void> singleProcessTester) {
        IOExceptionFunction<Void, Void> actionUnderTest = (Void arg) -> {
            // Do some artificial work here
            assertEquals(1, 1);
            return null;
        };
        for (int i = 0; i < lockFiles.length; i++) {
            File lockFile = lockFiles[i];
            LockType lockType = lockTypes[i];

            interProcessTester.addMainMethodInvocationFromNewProcess(
                    SampleAction.class, new String[] {lockFile.getPath(), lockType.toString()});

            singleProcessTester.addMethodInvocationFromNewThread(
                    (IOExceptionFunction<Void, Void> anActionUnderTest) -> {
                        executeActionWithLock(
                                () -> anActionUnderTest.apply(null),
                                lockFile,
                                lockType);
                    },
                    actionUnderTest);
        }
    }

    /** Private class whose main() method will execute a sample action. */
    private static final class SampleAction {

        public static void main(String[] args) throws IOException {
            File lockFile = new File(args[0]);
            LockType lockType = LockType.valueOf(args[1]);
            String[] remainingArgs = Arrays.copyOfRange(args, 2, args.length);

            executeActionWithLock(
                    () -> new InterProcessConcurrencyTester().runActionUnderTest(remainingArgs),
                    lockFile,
                    lockType);
        }
    }

    /** Executes an action with a lock. */
    private static void executeActionWithLock(
            @NonNull IOExceptionRunnable action,
            @NonNull File lockFile,
            @NonNull LockType lockType)
            throws IOException {
        ReadWriteProcessLock readWriteProcessLock = ReadWriteProcessLock.getInstance(lockFile);
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
}
