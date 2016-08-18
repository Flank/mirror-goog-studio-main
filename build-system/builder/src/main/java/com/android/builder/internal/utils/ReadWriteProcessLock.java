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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads both within the same
 * process and across different processes. The basic usages involving {@link #readLock()}, {@link
 * #writeLock()}, {@link Lock#lock()}, and {@link Lock#unlock()} are similar to those of {@link
 * ReentrantReadWriteLock} and {@link java.util.concurrent.locks.Lock}.
 */
public final class ReadWriteProcessLock {

    /**
     * Map from a lock file to an instance of {@code ReadWriteProcessLock}, used to make sure that
     * there is only one instance of {@code ReadWriteProcessLock} per lock file (within the same
     * process).
     */
    @NonNull
    private static final ConcurrentMap<File, ReadWriteProcessLock> sReadWriteProcessLockMap =
            new ConcurrentHashMap<>();

    @NonNull
    private final ReadWriteProcessLock.ReadLock mReadLock = new ReadWriteProcessLock.ReadLock();

    @NonNull
    private final ReadWriteProcessLock.WriteLock mWriteLock = new ReadWriteProcessLock.WriteLock();

    /** Internal ReadWriteLock to synchronize threads within the current process. */
    @NonNull private final ReadWriteLock mSingleProcessLock = new ReentrantReadWriteLock();

    /** Facility to synchronize processes. */
    @NonNull private final ProcessSynchronizer mProcessSynchronizer;

    /** Number of actions that are holding the read lock within the current process. */
    @GuardedBy("this")
    private int mNumOfReadingActions = 0;

    private ReadWriteProcessLock(@NonNull File lockFile) {
        mProcessSynchronizer = new ProcessSynchronizer(lockFile);
    }

    /**
     * Returns a {@code ReadWriteProcessLock} instance with a lock file as a way to synchronize
     * execution across processes. This method guarantees that there is only one {@code
     * ReadWriteProcessLock} instance per process for a given lock file (there could still be
     * multiple {@code ReadWriteProcessLock} instances on different processes for a given lock
     * file).
     *
     * @param lockFile the lock file, used solely for synchronization purposes
     */
    public static ReadWriteProcessLock getInstance(@NonNull File lockFile) throws IOException {
        return sReadWriteProcessLockMap.computeIfAbsent(
                lockFile.getCanonicalFile(),
                (canonicalLockFile) -> new ReadWriteProcessLock(canonicalLockFile));
    }

    /** Returns the lock used for reading. */
    public ReadWriteProcessLock.ReadLock readLock() {
        return mReadLock;
    }

    /** Returns the lock used for writing. */
    public ReadWriteProcessLock.WriteLock writeLock() {
        return mWriteLock;
    }

    private void acquireReadLock() throws IOException {
        // Acquire a read lock within the current process first
        mSingleProcessLock.readLock().lock();
        // At this point, there could be multiple threads in the current process having the read
        // lock. Let the first of these threads also acquire the read lock from other processes as
        // well. Note that the ProcessSynchronizer uses Java's file locking API (specifically
        // FileChannel.lock()), which allows only one thread per process to acquire a FileLock,
        // otherwise it will throw an OverlappingFileLockException, that's why we only let the first
        // thread acquire the file lock.
        try {
            synchronized (this) {
                if (mNumOfReadingActions == 0) {
                    mProcessSynchronizer.acquireReadLock();
                }
                mNumOfReadingActions++;
            }
        } catch (Throwable throwable) {
            // If an error occurred, release the read lock within the current process
            mSingleProcessLock.readLock().unlock();
            throw throwable;
        }
    }

    private void releaseReadLock() throws IOException {
        // Do the reverse of acquireReadLock()
        try {
            synchronized (this) {
                mNumOfReadingActions--;
                if (mNumOfReadingActions == 0) {
                    mProcessSynchronizer.releaseLock();
                }
            }
        } finally {
            // Whether an error occurred or not, release the read lock within the current process
            mSingleProcessLock.readLock().unlock();
        }
    }

    private void acquireWriteLock() throws IOException {
        // Acquire a write lock within the current process first
        mSingleProcessLock.writeLock().lock();
        // At this point, the current thread is the only thread in the current process that has the
        // write lock. Let the current thread also acquire a write lock from other processes as
        // well. Note that the ProcessSynchronizer uses Java's file locking API (specifically
        // FileChannel.lock()), which allows only one thread per process to acquire a FileLock,
        // otherwise it will throw an OverlappingFileLockException, that's why we need to acquire a
        // write lock within the current process first before acquiring the file lock.
        try {
            mProcessSynchronizer.acquireWriteLock();
        } catch (Throwable throwable) {
            // If an error occurred, release the write lock within the current process
            mSingleProcessLock.writeLock().unlock();
            throw throwable;
        }
    }

    private void releaseWriteLock() throws IOException {
        // Do the reverse of acquireWriteLock()
        try {
            mProcessSynchronizer.releaseLock();
        } finally {
            // Whether an error occurred or not, release the write lock within the current process
            mSingleProcessLock.writeLock().unlock();
        }
    }

    /** Facility to synchronize processes. */
    private static final class ProcessSynchronizer {

        @NonNull private final File mLockFile;

        @Nullable private FileChannel mFileChannel;

        @Nullable private FileLock mFileLock;

        public ProcessSynchronizer(@NonNull File lockFile) {
            mLockFile = lockFile;
        }

        public void acquireReadLock() throws IOException {
            mFileChannel = new RandomAccessFile(mLockFile, "rw").getChannel();
            mFileLock = mFileChannel.lock(0L, Long.MAX_VALUE, true);
        }

        public void acquireWriteLock() throws IOException {
            mFileChannel = new RandomAccessFile(mLockFile, "rw").getChannel();
            mFileLock = mFileChannel.lock(0L, Long.MAX_VALUE, false);
        }

        public void releaseLock() throws IOException {
            Preconditions.checkNotNull(mFileChannel);
            Preconditions.checkNotNull(mFileLock);

            // Delete the lock file first; if we delete the lock file after the file lock is
            // released, another process might have grabbed the file lock and we might be deleting
            // the file while the other process is using it.
            mLockFile.delete();
            mFileLock.release();
            mFileChannel.close();
        }
    }

    public interface Lock {

        void lock() throws IOException;

        void unlock() throws IOException;
    }

    @Immutable
    private final class ReadLock implements Lock {

        @Override
        public void lock() throws IOException {
            acquireReadLock();
        }

        @Override
        public void unlock() throws IOException {
            releaseReadLock();
        }
    }

    @Immutable
    private final class WriteLock implements Lock {

        @Override
        public void lock() throws IOException {
            acquireWriteLock();
        }

        @Override
        public void unlock() throws IOException {
            releaseWriteLock();
        }
    }
}
