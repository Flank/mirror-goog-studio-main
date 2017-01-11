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

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads both within the same
 * JVM and across different JVMs, even in the presence of multiple custom class loaders.
 *
 * <p>Typically, there exists only one JVM in a process. Therefore, we call this class {@code
 * ReadWriteProcessLock}, although in a multi-JVM process, this is not strictly correct. Also, from
 * here we will use the term JVM and process interchangeably.
 *
 * <p>Threads and processes will be synchronized on the same lock file (two lock files are the same
 * if one equals() the other). The client using {@code ReadWriteProcessLock} will provide a lock
 * file when constructing a {@code ReadWriteProcessLock} instance. Then, different threads and
 * processes using the same or different {@code ReadWriteProcessLock} instances on the same lock
 * file can be synchronized.
 *
 * <p>The basic usage of this class is similar to {@link ReentrantReadWriteLock} and {@link
 * java.util.concurrent.locks.Lock}. Below is a typical example.
 *
 * <pre>{@code
 * ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile);
 * ReadWriteProcessLock.Lock lock =
 *     useSharedLock
 *         ? readWriteProcessLock.readLock()
 *         : readWriteProcessLock.writeLock();
 * lock.lock();
 * try {
 *     runnable.run();
 * } finally {
 *     lock.unlock();
 * }
 * }</pre>
 *
 * <p>The key usage difference between {@code ReadWriteProcessLock} and a regular Java lock such as
 * {@link ReentrantReadWriteLock} is that {@code ReadWriteProcessLock} is itself not a lock object
 * (which threads and processes are directly synchronized on), but only a proxy to the actual lock
 * object (a lock file in this case). Therefore, there could be multiple instances of {@code
 * ReadWriteProcessLock} on the same lock file.
 *
 * <p>Another distinction is that two lock files are considered the same if one equals() the
 * other. Internally, this class normalizes the lock file's path so that the paths can be
 * correctly compared by equals(), but it is good practice for the client to also normalize the file
 * paths when creating {@code ReadWriteProcessLock} instances.
 *
 * <p>This lock is not reentrant.
 *
 * <p>This class is thread-safe.
 */
public final class ReadWriteProcessLock {

    @NonNull
    private final ReadWriteProcessLock.Lock readLock = new ReadWriteProcessLock.ReadLock();

    @NonNull
    private final ReadWriteProcessLock.Lock writeLock = new ReadWriteProcessLock.WriteLock();

    /**
     * JVM-wide map from a lock file to a {@link FileChannel}, used to make sure that there is only
     * one instance of {@code FileChannel} per lock file within the current process.
     */
    @NonNull
    private static final JvmWideVariable<ConcurrentMap<Path, FileChannel>> fileChannelMap =
            new JvmWideVariable<>(
                    ReadWriteProcessLock.class.getName(),
                    "fileChannelMap",
                    concurrentMapToken(Path.class, FileChannel.class),
                    new ConcurrentHashMap<>());

    /**
     * JVM-wide map from a lock file to a {@link FileLock}, used to make sure that there is only one
     * instance of {@code FileLock} per lock file within the current process.
     */
    @NonNull
    private static final JvmWideVariable<ConcurrentMap<Path, FileLock>> fileLockMap =
            new JvmWideVariable<>(
                    ReadWriteProcessLock.class.getName(),
                    "fileLockMap",
                    concurrentMapToken(Path.class, FileLock.class),
                    new ConcurrentHashMap<>());

    /**
     * The lock file, used solely for synchronization purposes.
     */
    @NonNull
    private final Path lockFile;

    /**
     * Lock used to synchronize threads within the current process.
     *
     * <p>Synchronization of processes is achieved via Java's file locking API (FileChannel.lock()).
     * However, Java does not allow the same process to acquire a FileLock twice (it will throw an
     * OverlappingFileLockException if a process has not released a FileLock object but attempts to
     * acquire another one on the same lock file). Therefore, we use this within-process lock to
     * synchronize threads within the same process first before using FileLock to synchronize
     * processes.
     */
    @NonNull
    private final ReadWriteThreadLock readWriteThreadLock;

    /**
     * The number of actions that are holding the within-process read lock on the given lock file.
     */
    @NonNull
    private final AtomicInteger numOfReadingActions;

    /**
     * Creates a {@link ReadWriteProcessLock} instance for the given lock file. Threads and
     * processes will be synchronized on the same lock file (two lock files are the same if one
     * equals() the other).
     *
     * <p>It is strongly recommended that the lock file is used solely for synchronization purposes.
     * The client of this class should not access (read, write, or delete) the lock file. The lock
     * file may or may not exist when this method is called. However, it will be created and will
     * not be deleted once the client starts using the locking mechanism provided by this class.
     * The client may delete the lock files only when the locking mechanism is no longer in use.
     *
     * <p>This method will normalize the lock file's path first so that the paths can be correctly
     * compared by equals().
     *
     * @param lockFile the lock file, used solely for synchronization purposes
     */
    public ReadWriteProcessLock(@NonNull Path lockFile) {
        // Normalize the lock file's path first
        Path normalizedLockFile = lockFile.normalize();

        this.lockFile = normalizedLockFile;
        this.readWriteThreadLock = new ReadWriteThreadLock(normalizedLockFile);

        // To make this.numOfReadingActions contain a value shared among all the threads, we use a
        // JvmWideVariable map
        JvmWideVariable<ConcurrentMap<Path, AtomicInteger>> map =
                new JvmWideVariable<>(
                        ReadWriteProcessLock.class.getName(),
                        "numOfReadingActions",
                        concurrentMapToken(Path.class, AtomicInteger.class),
                        new ConcurrentHashMap<>());
        this.numOfReadingActions =
                map.get().computeIfAbsent(lockFile, (Path) -> new AtomicInteger(0));
    }

    /** Returns the lock used for reading. */
    public ReadWriteProcessLock.Lock readLock() {
        return readLock;
    }

    /** Returns the lock used for writing. */
    public ReadWriteProcessLock.Lock writeLock() {
        return writeLock;
    }

    private void acquireReadLock() throws IOException  {
        // Acquire a read lock within the current process first
        readWriteThreadLock.readLock().lock();

        // At this point, there could be multiple threads in the current process having the
        // within-process read lock. If more than one of them attempt to acquire an inter-process
        // read lock (a shared FileLock), Java would throw an OverlappingFileLockException.
        // Therefore, we only let the first of these threads acquire a FileLock on behalf of the
        // entire process.
        try {
            synchronized (numOfReadingActions) {
                if (numOfReadingActions.get() == 0) {
                    acquireFileLock(true);
                }
                // We increase numOfReadingActions after the above acquireFileLock() so that if that
                // method throws an exception, numOfReadingActions remains 0
                numOfReadingActions.getAndIncrement();
            }
        } catch (Throwable throwable) {
            // If an error occurred, release the read lock within the current process
            readWriteThreadLock.readLock().unlock();
            throw throwable;
        }
    }

    private void releaseReadLock() throws IOException {
        // Do the reverse of acquireReadLock()
        try {
            synchronized (numOfReadingActions) {
                // We decrease numOfReadingActions before releaseFileLock() so that even if that
                // method throws an exception, numOfReadingActions is still decreased
                if (numOfReadingActions.decrementAndGet() == 0) {
                    releaseFileLock();
                }
            }
        } finally {
            // Whether an error occurred or not, release the read lock within the current process
            readWriteThreadLock.readLock().unlock();
        }
    }

    private void acquireWriteLock() throws IOException  {
        // Acquire a write lock within the current process first
        readWriteThreadLock.writeLock().lock();

        // At this point, the current thread is the only thread in the current process that has the
        // within-process write lock. Let the current thread also acquire an inter-process write
        // lock (an exclusive FileLock) on behalf of the entire process.
        try {
            acquireFileLock(false);
        } catch (Throwable throwable) {
            // If an error occurred, release the write lock within the current process
            readWriteThreadLock.writeLock().unlock();
            throw throwable;
        }
    }

    private void releaseWriteLock() throws IOException  {
        // Do the reverse of acquireWriteLock()
        try {
            releaseFileLock();
        } finally {
            // Whether an error occurred or not, release the write lock within the current process
            readWriteThreadLock.writeLock().unlock();
        }
    }

    private void acquireFileLock(boolean shared) throws IOException {
        // Within the current process, this method is never called from more than one thread and is
        // never called concurrently with releaseFileLock().
        // However, this method might be called from more than one process or might be called
        // concurrently with releaseFileLock() from another process. If after the current process
        // opens the file channel (thereby creating the lock file if it does not yet exist), another
        // process deletes the lock file, then the file lock used by the current process will become
        // ineffective. Therefore, we do not allow deleting lock files in this class. As long as the
        // lock files are not deleted, this method and releaseFileLock() are safe to be called from
        // multiple processes.
        Map<Path, FileChannel> channelMap = fileChannelMap.get();
        Map<Path, FileLock> lockMap = fileLockMap.get();

        Preconditions.checkState(
                !channelMap.containsKey(lockFile) && !lockMap.containsKey(lockFile),
                "acquireFileLock() must not be called twice"
                        + " (ReadWriteProcessLock is not reentrant)");

        FileChannel fileChannel =
                FileChannel.open(
                        lockFile,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
        FileLock fileLock = fileChannel.lock(0L, Long.MAX_VALUE, shared);

        channelMap.put(lockFile, fileChannel);
        lockMap.put(lockFile, fileLock);
    }

    private void releaseFileLock() throws IOException  {
        // As commented in acquireFileLock(), this method and acquireFileLock() are never called
        // from more than one thread per process and are safe to be called from multiple processes.
        Map<Path, FileChannel> channelMap = fileChannelMap.get();
        Map<Path, FileLock> lockMap = fileLockMap.get();

        FileChannel fileChannel = channelMap.get(lockFile);
        FileLock fileLock = lockMap.get(lockFile);

        Preconditions.checkNotNull(fileChannel);
        Preconditions.checkNotNull(fileLock);

        // We close the resources but do not delete the lock file as doing so would be unsafe to
        // other processes which might be using the same lock file (see the comments in
        // acquireFileLock()).
        fileLock.release();
        fileChannel.close();

        channelMap.remove(lockFile);
        lockMap.remove(lockFile);
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

    /**
     * Returns the {@link TypeToken} for a {@link ConcurrentMap}.
     */
    @NonNull
    private static <K, V> TypeToken<ConcurrentMap<K, V>> concurrentMapToken(
            @NonNull Class<K> keyClass, @NonNull Class<V> valueClass) {
        return new TypeToken<ConcurrentMap<K, V>>() {}
                .where(new TypeParameter<K>() {}, TypeToken.of(keyClass))
                .where(new TypeParameter<V>() {}, TypeToken.of(valueClass));
    }
}
