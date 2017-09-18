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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.JvmWideVariable;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A lock similar to {@link ReentrantReadWriteLock} used to synchronize threads both within the same
 * JVM and across different JVMs, even when classes (including this class) are loaded multiple times
 * by different class loaders.
 *
 * <p>Typically, there exists only one JVM in a process. Therefore, we call this class {@code
 * ReadWriteProcessLock}, although in a multi-JVM process, this is not strictly correct. Also, from
 * here we will use the term JVM and process interchangeably.
 *
 * <p>Threads and processes will be synchronized on the same lock file (two lock files are the same
 * if they refer to the same physical file). The client using {@code ReadWriteProcessLock} will
 * provide a lock file when constructing a {@code ReadWriteProcessLock} instance. Then, different
 * threads and processes using the same or different {@code ReadWriteProcessLock} instances on the
 * same lock file can be synchronized.
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
 * <p>The key usage differences between {@code ReadWriteProcessLock} and a regular Java lock such as
 * {@link ReentrantReadWriteLock} are:
 *
 * <ol>
 *   <li>{@code ReadWriteProcessLock} is itself not a lock object (which threads and processes are
 *       directly synchronized on), but a proxy to the actual lock object (a lock file in this
 *       case). Therefore, there could be multiple instances of {@code ReadWriteProcessLock} on the
 *       same lock file.
 *   <li>Two lock files are considered the same if they refer to the same physical file.
 * </ol>
 *
 * <p>This lock is not reentrant.
 *
 * <p>This class is thread-safe.
 */
public final class ReadWriteProcessLock {

    /**
     * Map from a lock file to a {@link FileChannel}, used to make sure that there is only one
     * instance of {@code FileChannel} per lock file within the current process.
     */
    @SuppressWarnings("ConstantConditions") // Guaranteed to be non-null
    @NonNull
    private static final ConcurrentMap<Path, FileChannel> fileChannelMap =
            new JvmWideVariable<>(
                            ReadWriteProcessLock.class,
                            "fileChannelMap",
                            new TypeToken<ConcurrentMap<Path, FileChannel>>() {},
                            ConcurrentHashMap::new)
                    .get();

    /**
     * Map from a lock file to a {@link FileLock}, used to make sure that there is only one instance
     * of {@code FileLock} per lock file within the current process.
     */
    @SuppressWarnings("ConstantConditions") // Guaranteed to be non-null
    @NonNull
    private static final ConcurrentMap<Path, FileLock> fileLockMap =
            new JvmWideVariable<>(
                            ReadWriteProcessLock.class,
                            "fileLockMap",
                            new TypeToken<ConcurrentMap<Path, FileLock>>() {},
                            ConcurrentHashMap::new)
                    .get();

    /**
     * Map from a lock file to an {@link AtomicInteger} that counts the number of actions that are
     * holding the within-process read lock on the given lock file.
     */
    @SuppressWarnings("ConstantConditions") // Guaranteed to be non-null
    @NonNull
    private static final ConcurrentMap<Path, AtomicInteger> numOfReadingActionsMap =
            new JvmWideVariable<>(
                            ReadWriteProcessLock.class,
                            "numOfReadingActionsMap",
                            new TypeToken<ConcurrentMap<Path, AtomicInteger>>() {},
                            ConcurrentHashMap::new)
                    .get();

    /** The lock used for reading. */
    @NonNull private final ReadWriteProcessLock.Lock readLock = new ReadWriteProcessLock.ReadLock();

    /** The lock used for writing. */
    @NonNull
    private final ReadWriteProcessLock.Lock writeLock = new ReadWriteProcessLock.WriteLock();

    /** The lock file, used solely for synchronization purposes. */
    @NonNull private final Path lockFile;

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
     * Creates a {@code ReadWriteProcessLock} instance for the given lock file. Threads and
     * processes will be synchronized on the same lock file (two lock files are the same if they
     * refer to the same physical file).
     *
     * <p>The lock file may or may not exist when this constructor is called. It will be created if
     * it does not yet exist and will not be deleted after this constructor is called.
     *
     * <p>In order for the lock file to be created (if it does not yet exist), the parent directory
     * of the lock file must exist when this constructor is called.
     *
     * <p>IMPORTANT: The lock file must be used solely for synchronization purposes. The client of
     * this class must not access (read, write, or delete) the lock file. The client may delete the
     * lock file only when the locking mechanism is no longer in use.
     *
     * <p>This constructor will normalize the lock file's path first to detect same physical files
     * via equals(), so the client does not need to normalize the file's path in advance.
     *
     * @param lockFile the lock file, used solely for synchronization purposes; it may not yet
     *     exist, but its parent directory must exist
     * @throws IllegalArgumentException if the parent directory of the lock file does not exist, or
     *     if a directory or a regular (non-empty) file with the same path as the lock file
     *     accidentally exists
     */
    public ReadWriteProcessLock(@NonNull Path lockFile) {
        try {
            lockFile = createAndNormalizeLockFile(lockFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.lockFile = lockFile;
        this.readWriteThreadLock = new ReadWriteThreadLock(lockFile);
        this.numOfReadingActions =
                numOfReadingActionsMap.computeIfAbsent(lockFile, (any) -> new AtomicInteger(0));
    }

    /**
     * Creates the lock file if it does not yet exist and normalizes its path.
     *
     * @return the normalized path to an existing lock file
     */
    @NonNull
    @VisibleForTesting
    static Path createAndNormalizeLockFile(@NonNull Path lockFilePath) throws IOException {
        // Create the lock file if it does not yet exist
        if (!Files.exists(lockFilePath)) {
            // This is needed for Files.exist() and Files.createFile() below to work properly
            lockFilePath = lockFilePath.normalize();

            Preconditions.checkArgument(
                    Files.exists(Verify.verifyNotNull(lockFilePath.getParent())),
                    "Parent directory of " + lockFilePath.toAbsolutePath() + " does not exist");
            try {
                Files.createFile(lockFilePath);
            } catch (FileAlreadyExistsException e) {
                // It's okay if the file has already been created (although we checked that the file
                // did not exist before creating the file, it is still possible that the file was
                // immediately created after the check by some other thread/process).
            }
        }

        // Normalize the lock file's path with Path.toRealPath() (Path.normalize() does not resolve
        // symbolic links)
        lockFilePath = lockFilePath.toRealPath();

        // Make sure that no directory with the same path as the lock file accidentally exists
        Preconditions.checkArgument(
                !Files.isDirectory(lockFilePath),
                lockFilePath.toAbsolutePath() + " is a directory.");

        // Make sure that no regular (non-empty) file with the same path as the lock file
        // accidentally exists
        long lockFileSize = Files.size(lockFilePath);
        Preconditions.checkArgument(
                lockFileSize == 0,
                String.format(
                        "File '%1$s' with size=%2$d cannot be used as a lock file.",
                        lockFilePath.toAbsolutePath(), lockFileSize));

        return lockFilePath;
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
        Preconditions.checkState(
                !fileChannelMap.containsKey(lockFile) && !fileLockMap.containsKey(lockFile),
                "acquireFileLock() must not be called twice"
                        + " (ReadWriteProcessLock is not reentrant)");

        FileChannel fileChannel =
                FileChannel.open(
                        lockFile,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
        FileLock fileLock;
        try {
            fileLock = fileChannel.lock(0L, Long.MAX_VALUE, shared);
        } catch (OverlappingFileLockException e) {
            throw new RuntimeException(
                    "Unable to acquire a file lock for " + lockFile.toAbsolutePath(), e);
        }

        fileChannelMap.put(lockFile, fileChannel);
        fileLockMap.put(lockFile, fileLock);
    }

    private void releaseFileLock() throws IOException  {
        // As commented in acquireFileLock(), this method and acquireFileLock() are never called
        // from more than one thread per process and are safe to be called from multiple processes.
        FileChannel fileChannel = fileChannelMap.get(lockFile);
        FileLock fileLock = fileLockMap.get(lockFile);

        Preconditions.checkNotNull(fileChannel);
        Preconditions.checkNotNull(fileLock);

        // We close the resources but do not delete the lock file as doing so would be unsafe to
        // other processes which might be using the same lock file (see the comments in
        // acquireFileLock()).
        fileLock.release();
        fileChannel.close();

        fileChannelMap.remove(lockFile);
        fileLockMap.remove(lockFile);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("lockFile", lockFile).toString();
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
