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
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. The main API
 * method {@link #createFile(File, Inputs, IOExceptionConsumer)} creates a file/directory by copying
 * it from the cache, or creating and caching it first if it does not already exist.
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
     * @param cacheDirectory the directory that will contain the cached files/directories
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
     * @param cacheDirectory the directory that will contain the cached files/directories
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
     * Creates a file/directory by copying it from the cache, or creating it first via a callback
     * function and caching it if the cached file/directory does not already exist.
     *
     * <p>To determine whether to reuse a cached file/directory or create a new file/directory, the
     * client needs to provide all the inputs that affect the creation of the output file/directory,
     * including input files/directories and other input parameters. If some inputs are missing
     * (e.g., {@code encoding=utf-8}), the client may reuse a cached file/directory that is
     * incorrect. On the other hand, if some irrelevant inputs are included (e.g., {@code
     * verbose=true}), the cache may create a new cached file/directory even though the same one
     * already exists. In other words, missing inputs affect correctness, and irrelevant inputs
     * affect performance. Thus, the client needs to consider carefully what to include and exclude
     * in these inputs. For example, it is important to select a combination of properties that
     * uniquely identify an input file/directory depending on a specific use case (common properties
     * of a file/directory include its path, name, hash, size, and timestamp). As another example,
     * if the client uses different commands or different versions of the same command to create the
     * output, then the commands or their versions also need to be specified as part of the inputs.
     *
     * <p>These input parameters are wrapped in the {@link Inputs} object with the order in which
     * they are added to the {@code Inputs} object. If this cache is invoked multiple times on the
     * same list of inputs, the first call will cache the output file/directory and subsequent calls
     * will reuse the cached file/directory.
     *
     * <p>The argument that this cache passed to the callback function is a file/directory to be
     * created (depending on the actual implementation of the cache, it may be an intermediate
     * file/directory and not the final output file/directory). Thus, the callback should not assume
     * that the passed-back argument is the final output file/directory. Before the callback is
     * invoked, this cache deletes the passed-back file/directory if it already exists and creates
     * its parent directory if it does not exist.
     *
     * <p>The callback is not required to always create the file/directory (e.g., we don't create
     * dex files for jars that don't contain class files). In such cases, the cache will remember
     * this result, and subsequent calls on the same list of inputs will produce no output.
     *
     * <p>Finally, the output file/directory is replaced if it already exists. (When no output is
     * produced, the output file/directory is still deleted if it exists.)
     *
     * @param outputFile the output file/directory
     * @param inputs all the inputs the affect the creation of the output file/directory
     * @param fileProducer the callback function to create the output file/directory
     */
    public void createFile(
            @NonNull File outputFile,
            @NonNull Inputs inputs,
            @NonNull IOExceptionConsumer<File> fileProducer)
            throws IOException {
        // For each unique list of inputs, we compute a unique key and use it as the name of the
        // cached file container. The cached file container is a directory that contains the actual
        // cached file and another file describing the inputs.
        File cachedFileContainer = new File(mCacheDirectory, inputs.getKey());
        File cachedFile = new File(cachedFileContainer, "output");
        File inputsFile = new File(cachedFileContainer, "inputs");

        // Action to create the cached file first if it does not already exist
        IOExceptionRunnable createCachedFileAction = () -> {
            if (!cachedFileContainer.exists()) {
                mMisses.incrementAndGet();
                try {
                    // Ask fileProducer to create the cached file. We use a generic name for the
                    // cached file instead of using the name of the output file itself since there
                    // can be multiple output files sharing the same cached file if the cache is
                    // used to create create different output files from the same list of inputs.
                    // However, we also need to make sure that the file passed to fileProducer has
                    // the same file name extension as that of the final output file; otherwise,
                    // fileProducer may not be able to create the file (e.g., a dx command will fail
                    // if the file's extension is missing). To do that, we first create a temporary
                    // file that has the same name as the final output file, then rename it to the
                    // cached file.
                    FileUtils.mkdirs(cachedFileContainer);
                    File tmpFile = new File(cachedFileContainer, outputFile.getName());
                    fileProducer.accept(tmpFile);

                    // Before renaming, check whether the temporary file exists since fileProducer
                    // is not required to always create a new file
                    if (tmpFile.exists() && !tmpFile.equals(cachedFile)) {
                        Files.move(tmpFile, cachedFile);
                    }

                    // Write the inputs to the inputs file for diagnostic purposes
                    Files.write(inputs.toString(), inputsFile, StandardCharsets.UTF_8);
                } catch (Exception exception) {
                    // If an exception occurred, clean up the cached file container directory
                    FileUtils.deletePath(cachedFileContainer);
                    throw exception;
                }
            } else {
                mHits.incrementAndGet();
            }
        };

        // Action to copy the cached file to the output file
        IOExceptionRunnable copyToOutputFileAction = () -> {
            if (cachedFile.exists()) {
                copyFileOrDirectory(cachedFile, outputFile);
            } else {
                FileUtils.deletePath(outputFile);
            }
        };

        doLocked(
                mCacheDirectory,
                LockingType.SHARED,
                () -> {
                    // Create (or recreate) the cache directory since it might not exist or
                    // might have been deleted. The following method is thread-safe so it's okay
                    // to call it from multiple threads (and processes).
                    FileUtils.mkdirs(mCacheDirectory);
                    // Create the cached file first if it does not already exist
                    doLocked(cachedFileContainer, LockingType.EXCLUSIVE, createCachedFileAction);
                    // Copy the cached file to the output file
                    doLocked(
                            cachedFileContainer,
                            LockingType.SHARED,
                            () -> doLocked(
                                    outputFile,
                                    LockingType.EXCLUSIVE,
                                    copyToOutputFileAction));
                });
    }

    /**
     * Deletes the cache directory and its contents.
     */
    public void delete() throws IOException {
        doLocked(
                mCacheDirectory,
                LockingType.EXCLUSIVE,
                () -> FileUtils.deletePath(mCacheDirectory));
    }

    /**
     * Executes an action that accesses a file/directory with a shared lock (for reading) or an
     * exclusive lock (for writing).
     *
     * <p>If this cache is configured with {@code INTER_PROCESS} locking scope, synchronization
     * takes place for threads of the same process as well as those across different processes. If
     * this cache is configured with {@code SINGLE_PROCESS} scope, synchronization takes place for
     * threads of the same process only and not for threads across different processes. In any case,
     * synchronization takes effect only for the same cache (i.e., processes/threads using different
     * cache directories are not synchronized.)
     *
     * <p>Note that the file/directory to be accessed may or may not already exist.
     *
     * @param accessedFile the file/directory that an action is going to access
     * @param lockingType the type of lock (shared/reading or exclusive/writing)
     * @param action the action that will be accessing the file/directory
     */
    @VisibleForTesting
    void doLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockingType,
            @NonNull IOExceptionRunnable action)
            throws IOException {
        if (mLockingScope == LockingScope.INTER_PROCESS) {
            doInterProcessLocked(accessedFile, lockingType, action);
        } else {
            doSingleProcessLocked(accessedFile, lockingType, action);
        }
    }

    /** Executes an action that accesses a file/directory with inter-process locking. */
    private void doInterProcessLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockType,
            @NonNull IOExceptionRunnable action)
            throws IOException {
        // For each file being accessed, we create a corresponding lock file to synchronize
        // execution across processes. Note that we don't use the file being accessed (which might
        // not already exist) as the lock file since we don't want it to be affected by our locking
        // mechanism (specifically, the locking mechanism will always create the lock file and
        // delete it after the action is executed; however, an action may or may not create the file
        // that it is supposed to access).
        String lockFileName =
                Hashing.sha1()
                        .hashString(
                                FileUtils.getCaseSensitivityAwareCanonicalPath(accessedFile),
                                StandardCharsets.UTF_8)
                        .toString();
        // If the file being accessed is the cache directory itself, then we create the lock file
        // within the tmpdir directory; otherwise we create it within the cache directory
        File lockFile =
                accessedFile.getCanonicalFile().equals(mCacheDirectory)
                        ? new File(System.getProperty("java.io.tmpdir"), lockFileName)
                        : new File(mCacheDirectory, lockFileName);

        // The contract of ReadWriteProcessLock.getInstance() makes sure that there is only one
        // ReadWriteProcessLock instance (per process) for a given lock file
        ReadWriteProcessLock readWriteProcessLock = ReadWriteProcessLock.getInstance(lockFile);
        ReadWriteProcessLock.Lock lock =
                lockType == LockingType.SHARED
                        ? readWriteProcessLock.readLock()
                        : readWriteProcessLock.writeLock();
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /** Executes an action that accesses a file/directory with single-process locking. */
    private void doSingleProcessLocked(
            @NonNull File accessedFile,
            @NonNull LockingType lockType,
            @NonNull IOExceptionRunnable action)
            throws IOException {
        ReadWriteLock readWriteLock =
                mLockMap.computeIfAbsent(
                        accessedFile.getCanonicalFile(),
                        (canonicalAccessedFile) -> new ReentrantReadWriteLock());
        Lock lock =
                lockType == LockingType.SHARED
                        ? readWriteLock.readLock()
                        : readWriteLock.writeLock();
        lock.lock();
        try {
            action.run();
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
     */
    private static void copyFileOrDirectory(@NonNull File from, @NonNull File to)
            throws IOException {
        assert from.exists() : "Source path " + from.getPath() + "does not exist.";

        if (!from.getCanonicalFile().equals(to.getCanonicalFile())) {
            if (from.isFile()) {
                Files.createParentDirs(to);
                FileUtils.copyFile(from, to);
            } else if (from.isDirectory()) {
                FileUtils.deletePath(to);
                FileUtils.copyDirectory(from, to);
            }
        }
    }

    /**
     * List of input parameters to be provided by the client when calling method {@link
     * FileCache#createFile(File, Inputs, IOExceptionConsumer)}.
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
             * unlikely to happen, it is possible). Depending on your specific use case, consider
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
                if (mParameters.isEmpty()) {
                    throw new IllegalStateException("Inputs must not be empty.");
                }
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
         * Returns a key representing this list of input parameters. They input parameters have the
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
