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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. The main API
 * method {@link #createFile(File, Inputs, Callable)} creates an output file/directory by either
 * copying it from the cache, or creating it first and caching it if the cached file/directory does
 * not already exist.
 */
@Immutable
public final class FileCache {

    /**
     * The scope of the locking facility. With {@code INTER_PROCESS} scope, synchronization takes
     * place for threads of the same process as well as those across different processes. With
     * {@code SINGLE_PROCESS} scope, synchronization takes place for threads of the same process
     * only and not for threads across different processes. Note that the locking can be in the form
     * of a shared lock and an exclusive lock depending on {@code LockingType}.
     */
    private enum LockingScope {

        /**
         * Synchronization for threads of the same process as well as those across different
         * processes.
         */
        INTER_PROCESS,

        /**
         * Synchronization for threads of the same process only and not for threads across different
         * processes.
         */
        SINGLE_PROCESS
    }

    /**
     * The type of the locking facility, similar to those used in a {@link ReadWriteLock}. Note that
     * the locking can take place within a single process or across different processes depending on
     * {@code LockingScope}.
     */
    @VisibleForTesting
    enum LockingType {

        /** Shared lock used for reading. */
        SHARED,

        /** Exclusive lock used for writing. */
        EXCLUSIVE
    }

    /**
     * Map from a cache directory to an instance of {@code FileCache}, used to make sure that there
     * is only one instance of {@code FileCache} per cache directory (within the same process).
     */
    @NonNull
    private static final ConcurrentMap<File, FileCache> sFileCacheMap = new ConcurrentHashMap<>();

    @NonNull private final File mCacheDirectory;

    @NonNull private final LockingScope mLockingScope;

    /**
     * Map from an entry in the cache to an instance of {@code ReentrantReadWriteLock}, used to make
     * sure that there is only once instance of {@code ReentrantReadWriteLock} per entry.
     */
    @NonNull
    private final ConcurrentMap<File, ReentrantReadWriteLock> mLockMap = new ConcurrentHashMap<>();

    // Additional fields used for testing only
    @NonNull private final AtomicInteger mMisses = new AtomicInteger(0);
    @NonNull private final AtomicInteger mHits = new AtomicInteger(0);

    private FileCache(@NonNull File canonicalCacheDirectory, @NonNull LockingScope lockingScope) {
        mCacheDirectory = canonicalCacheDirectory;
        mLockingScope = lockingScope;
    }

    private static FileCache getInstance(
            @NonNull File cacheDirectory, @NonNull LockingScope lockingScope) throws IOException {
        FileCache fileCache =
                sFileCacheMap.computeIfAbsent(
                        cacheDirectory.getCanonicalFile(),
                        (canonicalCacheDirectory) ->
                                new FileCache(canonicalCacheDirectory, lockingScope));

        if (lockingScope != fileCache.mLockingScope) {
            if (lockingScope == LockingScope.INTER_PROCESS) {
                throw new IllegalStateException(
                        "Unable to create FileCache with inter-process locking enabled"
                                + " since inter-process locking was previously disabled"
                                + " on the same cache.");
            } else {
                throw new IllegalStateException(
                        "Unable to create FileCache with inter-process locking disabled"
                                + " since inter-process locking was previously enabled"
                                + " on the same cache.");
            }
        }

        return fileCache;
    }

    /**
     * Returns a {@code FileCache} instance with inter-process locking (i.e., synchronization takes
     * place for threads of the same process as well as those across different processes). This
     * method guarantees that there is only one {@code FileCache} instance per process for a given
     * cache directory (there could still be multiple {@code FileCache} instances on different
     * processes for a given cache directory).
     *
     * @param cacheDirectory the directory that will contain the cached files/directories (may not
     *     already exist)
     */
    @NonNull
    public static FileCache getInstanceWithInterProcessLocking(@NonNull File cacheDirectory)
            throws IOException {
        return getInstance(cacheDirectory, LockingScope.INTER_PROCESS);
    }

    /**
     * Returns a {@code FileCache} instance with single-process locking (i.e., synchronization takes
     * place for threads of the same process only and not for threads across different processes).
     * This method guarantees that there is only one {@code FileCache} instance per process for a
     * given cache directory (there could still be multiple {@code FileCache} instances on different
     * processes for a given cache directory).
     *
     * @param cacheDirectory the directory that will contain the cached files/directories (may not
     *     already exist)
     */
    @NonNull
    public static FileCache getInstanceWithSingleProcessLocking(@NonNull File cacheDirectory)
            throws IOException {
        return getInstance(cacheDirectory, LockingScope.SINGLE_PROCESS);
    }

    @NonNull
    public File getCacheDirectory() {
        return mCacheDirectory;
    }

    /**
     * Creates an output file/directory by either copying it from the cache, or creating it first
     * via the given file producer callback function and caching it if the cached file/directory
     * does not already exist.
     *
     * <p>The output file/directory must not reside in, contain, or be identical to the cache
     * directory.
     *
     * <p>To determine whether to reuse a cached file/directory or create a new file/directory, the
     * client needs to provide all the inputs that affect the creation of the output file/directory,
     * including input files/directories and other input parameters to the {@link Inputs} object. If
     * some inputs are missing (e.g., {@code encoding=utf-8}), the client may reuse a cached
     * file/directory that is incorrect. On the other hand, if some irrelevant inputs are included
     * (e.g., {@code verbose=true}), the cache may create a new cached file/directory even though an
     * existing one can be used. In other words, missing inputs affect correctness, and irrelevant
     * inputs affect performance. Thus, the client needs to consider carefully what to include and
     * exclude in these inputs. For example, it is important to select a combination of properties
     * that uniquely identify an input file/directory depending on the specific use case (common
     * properties of a file/directory include its path, name, hash, size, and timestamp). As another
     * example, if the client uses different commands or different versions of the same command to
     * create the output, then the commands or their versions also need to be specified as part of
     * the inputs.
     *
     * <p>If this method is called multiple times on the same list of inputs, the first call will
     * invoke the file producer and cache the output file/directory and subsequent calls will reuse
     * the cached file/directory without invoking the file producer. Specifically, on the first
     * call, the cache deletes the output file/directory (if it exists), creates its parent
     * directories, invokes the file producer to create the output file/directory, and copies the
     * output file/directory to the cache. On subsequent calls, the cache will create/replace the
     * output file/directory with the cached result.
     *
     * <p>Note that the file producer is not required to always create an output (e.g., it may not
     * create dex files for jars that don't contain class files). If the file producer does not
     * create an output on the first call, the cache will remember this result and produce no output
     * on subsequent calls (it will still delete the output file/directory if it exists and creates
     * its parent directory on both the first and subsequent calls).
     *
     * <p>If this cache is configured with {@link LockingScope#INTER_PROCESS}, threads of the same
     * process as well as those across different processes can read same the cache entry
     * concurrently, but only one of them can write to the same cache entry or the same output
     * file/directory at a time, without any other thread concurrently reading that cache entry. If
     * this cache is configured with {@link LockingScope#SINGLE_PROCESS}, threads of the same
     * process as well as those across different processes can read same the cache entry
     * concurrently; however, only one thread in the current process can write to the same cache
     * entry or the same output file/directory at a time, without any other thread in the current
     * process concurrently reading that cache entry, but there is no guarantee that that cache
     * entry is not being read or written to by another thread of another process. The advantage of
     * having {@code SINGLE_PROCESS} locking scope is that the cache will incur less synchronization
     * overhead than with {@code INTER_PROCESS} locking scope. However, if the cache may be used by
     * more than one process at a time, then the client must configure the cache with {@code
     * INTER_PROCESS} locking scope.
     *
     * <p>If the cache is being deleted by another thread of any process in the case of {@code
     * INTER_PROCESS} locking scope, or by another thread of the current process in the case of
     * {@code SINGLE_PROCESS} locking scope (i.e., another thread is calling the {@link #delete()}
     * method on this cache), then this method will block until the deletion completes and will
     * then re-create the cache directory.
     *
     * <p>The user is strongly advised to access the cache via {@code FileCache}'s API; otherwise,
     * the above concurrency guarantees will no longer hold.
     *
     * <p>When this method throws an exception, the client can check whether the exception is caused
     * by the file producer or not. If the thrown exception has type {@link ExecutionException},
     * it means that an exception occurred during the execution of the file producer. If the thrown
     * exception has type {@link IOException} or {@link RuntimeException}, it means that the
     * exception occurred during the execution of this method, but not because of the file producer.
     *
     * @param outputFile the output file/directory
     * @param inputs all the inputs the affect the creation of the output file/directory
     * @param fileProducer the callback function to create the output file/directory
     * @throws ExecutionException if an exception occurs during the execution of the file producer
     * @throws IOException if an I/O exception occurs during the execution of this method, but not
     *     because of the file producer
     * @throws RuntimeException if a runtime exception occurs during the execution of this method,
     *     but not because of the file producer
     */
    public void createFile(
            @NonNull File outputFile,
            @NonNull Inputs inputs,
            @NonNull Callable<Void> fileProducer)
            throws ExecutionException, IOException {
        Preconditions.checkArgument(
                !FileUtils.isFileInDirectory(outputFile, mCacheDirectory),
                String.format(
                        "Output file/directory '%1$s' must not be located"
                                + " in the cache directory '%2$s'",
                        outputFile.getAbsolutePath(), mCacheDirectory.getAbsolutePath()));
        Preconditions.checkArgument(
                !FileUtils.isFileInDirectory(mCacheDirectory, outputFile),
                String.format(
                        "Output directory '%1$s' must not contain the cache directory '%2$s'",
                        outputFile.getAbsolutePath(), mCacheDirectory.getAbsolutePath()));
        Preconditions.checkArgument(
                !outputFile.getCanonicalFile().equals(mCacheDirectory.getCanonicalFile()),
                String.format(
                        "Output directory must not be the same as the cache directory '%1$s'",
                        mCacheDirectory.getAbsolutePath()));

        // For each unique list of inputs, we compute a unique key and use it as the name of the
        // cache entry directory. The cache entry directory is a directory that contains the actual
        // cached file and an inputs file describing the inputs.
        File cacheEntryDir = new File(mCacheDirectory, inputs.getKey());
        File cachedFile = new File(cacheEntryDir, "output");
        File inputsFile = new File(cacheEntryDir, "inputs");

        // Callable to create the output file
        Callable<Void> createOutputFile = () -> {
            // Delete the output file and create its parent directory first according to the
            // contract of this method
            FileUtils.deletePath(outputFile);
            Files.createParentDirs(outputFile);
            try {
                fileProducer.call();
            } catch (Exception exception) {
                throw new FileProducerException(exception);
            }
            return null;
        };

        // Callable to copy the output file to the cached file
        Callable<Void> copyOutputFileToCachedFile = () -> {
            // Only copy if the output file exists as file producer is not required to always
            // produce an output
            if (outputFile.exists()) {
                copyFileOrDirectory(outputFile, cachedFile);
            }
            return null;
        };

        // Callable to copy the cached file to the output file
        Callable<Void> copyCachedFileToOutputFile = () -> {
            // Delete the output file and create its parent directory according to the contract of
            // this method
            FileUtils.deletePath(outputFile);
            Files.createParentDirs(outputFile);
            // Only copy if the cached file exist as file producer may not have produced an output
            // during the first time this cache is called on the given inputs
            if (cachedFile.exists()) {
                copyFileOrDirectory(cachedFile, outputFile);
            }
            return null;
        };

        try {
            doLocked(mCacheDirectory, LockingType.SHARED, () -> {
                // Create (or recreate) the cache directory since it may not exist or might have
                // been deleted. The following method is thread-safe so it's okay to call it from
                // multiple threads (and processes).
                FileUtils.mkdirs(mCacheDirectory);

                // Copy the cached file to the output file if the cache entry directory exists
                boolean isHit = doLocked(cacheEntryDir, LockingType.SHARED, () -> {
                    if (cacheEntryDir.exists()) {
                        mHits.incrementAndGet();
                        doLocked(outputFile, LockingType.EXCLUSIVE, copyCachedFileToOutputFile);
                        return true;
                    } else {
                        return false;
                    }
                });
                if (isHit) {
                    return null;
                }

                doLocked(cacheEntryDir, LockingType.EXCLUSIVE, () -> {
                    // Check again if the cache entry directory exists as it might have been created
                    // by another thread/process since the last time we checked for its existence.
                    // If it exists, copy the cached file to the output file, this time with an
                    // EXCLUSIVE lock on the cache entry directory instead of a SHARED lock as
                    // before.
                    if (cacheEntryDir.exists()) {
                        mHits.incrementAndGet();
                        doLocked(outputFile, LockingType.EXCLUSIVE, copyCachedFileToOutputFile);
                    } else {
                        // If the cache entry directory does not exist, create the output file and
                        // copy it to the cached file
                        mMisses.incrementAndGet();
                        try {
                            FileUtils.mkdirs(cacheEntryDir);
                            doLocked(outputFile, LockingType.EXCLUSIVE, () -> {
                                createOutputFile.call();
                                copyOutputFileToCachedFile.call();
                                return null;
                            });
                            // Write the inputs to the inputs file for diagnostic purposes
                            Files.write(inputs.toString(), inputsFile, StandardCharsets.UTF_8);
                        } catch (Exception exception) {
                            // If an exception occurs, clean up the cache entry directory
                            FileUtils.deletePath(cacheEntryDir);
                            throw exception;
                        }
                    }
                    return null;
                });
                return null;
            });
        } catch (ExecutionException exception) {
            // If FileProducerException was thrown, it will be wrapped by a chain of execution
            // exceptions (due to nested doLocked() method calls), so let's iterate through the
            // causal chain and find out.
            for (Throwable cause : Throwables.getCausalChain(exception)) {
                if (cause instanceof FileProducerException) {
                    // Externally, FileProducerException is regarded as ExecutionException (see the
                    // javadoc of this method), so we simply rethrow the exception here.
                    throw exception;
                } else if (cause instanceof IOException) {
                    throw new IOException(exception);
                } else if (cause instanceof ExecutionException) {
                    continue;
                } else {
                    // If none of the previous exceptions is the cause, then the cause must be a
                    // RuntimeException since the previous exceptions are the only checked
                    // exceptions that we thew in this method.
                    throw new RuntimeException(exception);
                }
            }
            // We should never get to this line, but if we do, then we have some implementation
            // error, let's throw a runtime exception to indicate that.
            throw new RuntimeException(
                    "Unable to find root cause of ExecutionException, " + exception);
        }
    }

    /**
     * Deletes the cache directory and its contents.
     *
     * <p>If the cache is being used by another thread of any process in the case of {@code
     * INTER_PROCESS} locking scope, or by another thread of the current process in the case of
     * {@code SINGLE_PROCESS} locking scope, then this method will block until that operation
     * completes.
     */
    public void delete() throws IOException {
        try {
            doLocked(
                    mCacheDirectory,
                    LockingType.EXCLUSIVE,
                    () -> {
                        FileUtils.deletePath(mCacheDirectory);
                        return null;
                    });
        } catch (ExecutionException exception) {
            // The deletion action above does not throw any checked exceptions other than
            // IOException.
            if (exception.getCause() instanceof IOException) {
                throw new IOException(exception);
            } else {
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Executes an action that accesses a file/directory with a shared lock (for reading) or an
     * exclusive lock (for writing).
     *
     * <p>If this cache is configured with {@link LockingScope#INTER_PROCESS}, synchronization takes
     * place for threads of the same process as well as those across different processes. If this
     * cache is configured with {@link LockingScope#SINGLE_PROCESS}, synchronization takes place for
     * threads of the same process only and not for threads across different processes. In any case,
     * synchronization takes effect only for the same cache (i.e., processes/threads using different
     * cache directories are not synchronized).
     *
     * <p>Note that the file/directory to be accessed may or may not already exist.
     *
     * @param accessedFile the file/directory that an action is going to access
     * @param lockingType the type of lock (shared/reading or exclusive/writing)
     * @param action the action that will be accessing the file/directory
     * @throws ExecutionException if an exception occurs during the execution of the action
     * @throws IOException if an I/O exception occurs during the execution of this method, but not
     *     because of the action
     * @throws RuntimeException if a runtime exception occurs during the execution of this method,
     *     but not because of the action
     */
    @VisibleForTesting
    <V> V doLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockingType,
            @NonNull Callable<V> action)
            throws ExecutionException, IOException {
        if (mLockingScope == LockingScope.INTER_PROCESS) {
            return doInterProcessLocked(accessedFile, lockingType, action);
        } else {
            return doSingleProcessLocked(accessedFile, lockingType, action);
        }
    }

    /** Executes an action that accesses a file/directory with inter-process locking. */
    private <V> V doInterProcessLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockingType,
            @NonNull Callable<V> action)
            throws ExecutionException, IOException {
        // For each file/directory being accessed, we create a corresponding lock file to
        // synchronize execution across processes. We don't use the file/directory being accessed as
        // the lock file since we want to separate its usage from the locking mechanism and it is
        // also not possible for the underlying locking mechanism (using Java's FileLock) to lock a
        // directory.
        String lockFileName =
                Hashing.sha1()
                        .hashString(
                                FileUtils.getCaseSensitivityAwareCanonicalPath(accessedFile),
                                StandardCharsets.UTF_8)
                        .toString();
        // If the file/directory being accessed is the cache directory itself, we use a lock file
        // within the tmpdir directory; otherwise we use a lock file within the cache directory
        File lockFile =
                accessedFile.getCanonicalFile().equals(mCacheDirectory)
                        ? new File(System.getProperty("java.io.tmpdir"), lockFileName)
                        : new File(mCacheDirectory, lockFileName);

        // The contract of ReadWriteProcessLock.getInstance() makes sure that there is only one
        // ReadWriteProcessLock instance (per process) for a given lock file
        ReadWriteProcessLock readWriteProcessLock = ReadWriteProcessLock.getInstance(lockFile);
        ReadWriteProcessLock.Lock lock =
                lockingType == LockingType.SHARED
                        ? readWriteProcessLock.readLock()
                        : readWriteProcessLock.writeLock();
        lock.lock();
        try {
            return action.call();
        } catch (Exception exception) {
            throw new ExecutionException(exception);
        } finally {
            lock.unlock();
        }
    }

    /** Executes an action that accesses a file/directory with single-process locking. */
    private <V> V doSingleProcessLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockingType,
            @NonNull Callable<V> action)
            throws ExecutionException, IOException {
        ReadWriteLock readWriteLock =
                mLockMap.computeIfAbsent(
                        accessedFile.getCanonicalFile(),
                        (canonicalAccessedFile) -> new ReentrantReadWriteLock());
        Lock lock =
                lockingType == LockingType.SHARED
                        ? readWriteLock.readLock()
                        : readWriteLock.writeLock();
        lock.lock();
        try {
            return action.call();
        } catch (Exception exception) {
            throw new ExecutionException(exception);
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    int getMisses() {
        return mMisses.get();
    }

    @VisibleForTesting
    int getHits() {
        return mHits.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cacheDirectory", mCacheDirectory)
                .add("lockingScope", mLockingScope)
                .toString();
    }

    /**
     * Copies a file or a directory's contents to another file or directory, which can have a
     * different name. The target file/directory is replaced if it already exists.
     *
     * <p>The source file/directory must exist and must not reside in, contain, or be identical to
     * the target file/directory.
     */
    private static void copyFileOrDirectory(@NonNull File from, @NonNull File to)
            throws IOException {
        Preconditions.checkArgument(
                from.exists(), "Source path " + from.getAbsolutePath() + " does not exists.");
        Preconditions.checkArgument(!FileUtils.isFileInDirectory(from, to));
        Preconditions.checkArgument(!FileUtils.isFileInDirectory(to, from));
        Preconditions.checkArgument(!from.getCanonicalFile().equals(to.getCanonicalFile()));

        if (from.isFile()) {
            Files.createParentDirs(to);
            FileUtils.copyFile(from, to);
        } else if (from.isDirectory()) {
            FileUtils.deletePath(to);
            FileUtils.copyDirectory(from, to);
        }
    }

    /**
     * Checked exception thrown when the file producer callback function aborts due to an {@link
     * Exception}. This class is a private sub-class of {@link ExecutionException} and is used to
     * distinguish itself from other execution exceptions thrown elsewhere.
     */
    @Immutable
    private static final class FileProducerException extends ExecutionException {

        public FileProducerException(@NonNull Exception exception) {
            super(exception);
        }
    }

    /**
     * List of input parameters to be provided by the client when calling method {@link
     * FileCache#createFile(File, Inputs, Callable)}. The input parameters have the order in which
     * they were added to this {@code Inputs} object. If a parameter with the same name exists, the
     * parameter's value is overwritten (retaining its previous order).
     */
    @Immutable
    public static final class Inputs {

        @NonNull private final LinkedHashMap<String, String> mParameters;

        /** Builder of {@link FileCache.Inputs}. */
        public static final class Builder {

            @NonNull
            private final LinkedHashMap<String, String> mParameters = Maps.newLinkedHashMap();

            /**
             * Adds the path of a file/directory as an input parameter. If a parameter with the same
             * name exists, the file/directory's path is overwritten.
             *
             * <p>Note that this method is used to indicate that the path of an input file/directory
             * affects the output. Depending on your specific use case, consider adding other
             * properties of the file/directory such as its hash, size, or timestamp as part of the
             * inputs as well.
             */
            public Builder putFilePath(@NonNull String name, @NonNull File file) {
                mParameters.put(name, file.getPath());
                return this;
            }

            /**
             * Adds the hash of a file's contents as an input parameter. If a parameter with the
             * same name exists, the file's hash is overwritten.
             *
             * <p>Note that this method is used to indicate that the contents of an input file
             * affect the output. Also, by using this method, you are assuming that the hash of a
             * file represents its contents, so beware of accidental hash collisions when two
             * different files with different contents end up having the same hash (although it is
             * unlikely to happen, it is possible). Depending on the specific use case, consider
             * adding other properties of the file such as its path, name, size, or timestamp as
             * part of the inputs as well.
             *
             * @param file the file to be hashed (must not be a directory)
             */
            public Builder putFileHash(@NonNull String name, @NonNull File file)
                    throws IOException {
                Preconditions.checkArgument(file.isFile(), file + " is not a file.");

                mParameters.put(name, Files.hash(file, Hashing.sha1()).toString());
                return this;
            }

            /**
             * Adds an input parameter with a String value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder putString(@NonNull String name, @NonNull String value) {
                mParameters.put(name, value);
                return this;
            }

            /**
             * Adds an input parameter with a Boolean value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder putBoolean(@NonNull String name, @NonNull boolean value) {
                mParameters.put(name, String.valueOf(value));
                return this;
            }

            /**
             * Builds an {@code Inputs} instance.
             *
             * @throws IllegalStateException if the inputs are empty
             */
            public Inputs build() {
                Preconditions.checkState(!mParameters.isEmpty(), "Inputs must not be empty.");
                return new Inputs(this);
            }
        }

        private Inputs(@NonNull Builder builder) {
            mParameters = Maps.newLinkedHashMap(builder.mParameters);
        }

        @Override
        @NonNull
        public String toString() {
            return Joiner.on(System.lineSeparator()).withKeyValueSeparator("=").join(mParameters);
        }

        /**
         * Returns a key representing this list of input parameters. The input parameters have the
         * order in which they were added to this {@code Inputs} object. Two lists of input
         * parameters are considered different if the input parameters are different in size, order,
         * or values. This method guarantees to return different keys for different lists of inputs.
         */
        @NonNull
        public String getKey() {
            return Hashing.sha1().hashString(toString(), StandardCharsets.UTF_8).toString();
        }
    }
}
