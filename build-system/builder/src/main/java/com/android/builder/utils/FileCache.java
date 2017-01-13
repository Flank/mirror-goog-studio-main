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

package com.android.builder.utils;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.util.ReadWriteProcessLock;
import com.android.ide.common.util.ReadWriteThreadLock;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Cache for already-created files/directories.
 *
 * <p>This class is used to avoid creating the same file/directory multiple times. The main API
 * method {@link #createFile(File, Inputs, Callable)} creates an output file/directory by either
 * copying it from the cache, or creating it first and caching it if the cached file/directory does
 * not yet exist. Similarly, the {@link #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)}
 * method returns the cached output file/directory, and creates it first if the cached
 * file/directory does not yet exist.
 *
 * <p>Note that if a cache entry exists but is found to be corrupted, the cache entry will be
 * deleted and recreated.
 *
 * <p>This class is thread-safe.
 */
@Immutable
public class FileCache {

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

    @NonNull private final File mCacheDirectory;

    @NonNull private final LockingScope mLockingScope;

    // Additional fields used for testing only
    @NonNull private final AtomicInteger mMisses = new AtomicInteger(0);
    @NonNull private final AtomicInteger mHits = new AtomicInteger(0);

    private FileCache(@NonNull File cacheDirectory, @NonNull LockingScope lockingScope)
            throws IOException {
        mCacheDirectory = cacheDirectory.getCanonicalFile();
        mLockingScope = lockingScope;
    }

    /**
     * Returns a {@code FileCache} instance with {@link LockingScope#INTER_PROCESS}.
     *
     * <p>Threads of the same process as well as those across different processes can read same the
     * cache entry concurrently, but only one of them can write to the same cache entry or the same
     * output file/directory at a time, without any other thread concurrently reading that cache
     * entry. If the cache is being deleted by another thread of any process, then any read or write
     * access to the cache will block until the deletion completes. The user must access the cache
     * via {@code FileCache}'s API; otherwise, the previous concurrency guarantees will no longer
     * hold.
     *
     * <p>Note that if this cache is never used by more than one process at a time, it may be
     * preferable to configure the cache with {@code SINGLE_PROCESS} locking scope instead since
     * there will be less synchronization overhead.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories (may not
     *     already exist)
     * @see #getInstanceWithSingleProcessLocking(File)
     */
    @NonNull
    public static FileCache getInstanceWithInterProcessLocking(@NonNull File cacheDirectory)
            throws IOException {
        return new FileCache(cacheDirectory, LockingScope.INTER_PROCESS);
    }

    /**
     * Returns a {@code FileCache} instance with {@link LockingScope#SINGLE_PROCESS}.
     *
     * <p>Threads of the same process as well as those across different processes can read same the
     * cache entry concurrently; however, only one thread in the current process can write to the
     * same cache entry or the same output file/directory at a time, without any other thread in the
     * current process concurrently reading that cache entry, but there is no guarantee that that
     * cache entry is not being read or written to by another thread of another process. If the
     * cache is being deleted by another thread of the current process, then any read or write
     * access to the cache will block until the deletion completes. The user must access the cache
     * via {@code FileCache}'s API; otherwise, the previous concurrency guarantees will no longer
     * hold.
     *
     * <p>Note that if this cache may be used by more than one process at a time, the client must
     * configure the cache with {@code INTER_PROCESS} locking scope instead, even though there
     * will be more synchronization overhead.
     *
     * @param cacheDirectory the directory that will contain the cached files/directories (may not
     *     already exist)
     * @see #getInstanceWithInterProcessLocking(File)
     */
    @NonNull
    public static FileCache getInstanceWithSingleProcessLocking(@NonNull File cacheDirectory)
            throws IOException {
        return new FileCache(cacheDirectory, LockingScope.SINGLE_PROCESS);
    }

    @NonNull
    public File getCacheDirectory() {
        return mCacheDirectory;
    }

    /**
     * Creates an output file/directory by either copying it from the cache, or creating it first
     * via the given file creator callback function and caching it if the cached file/directory does
     * not yet exist.
     *
     * <p>The output file/directory must not reside in, contain, or be identical to the cache
     * directory.
     *
     * <p>To determine whether to reuse a cached file/directory or create a new file/directory, the
     * client needs to provide all the inputs that affect the creation of the output file/directory
     * to the {@link Inputs} object.
     *
     * <p>If this method is called multiple times on the same list of inputs, on the first call, the
     * cache will delete the output file/directory (if it exists), create its parent directories,
     * invoke the file creator to create the output file/directory, and copy the output
     * file/directory to the cache. On subsequent calls, the cache will create/replace the output
     * file/directory with the cached result without invoking the file creator.
     *
     * <p>Note that the file creator is not required to always create an output. If the file creator
     * does not create an output on the first call, the cache will remember this result and produce
     * no output on subsequent calls (it will still delete the output file/directory if it exists
     * and creates its parent directory on both the first and subsequent calls).
     *
     * <p>Depending on whether there are other threads/processes concurrently accessing this cache
     * and the type of locking scope configured for this cache, this method may block until it is
     * allowed to continue.
     *
     * @param outputFile the output file/directory
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param fileCreator the callback function to create the output file/directory
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     * @throws ExecutionException if an exception occurred during the execution of the file creator
     * @throws IOException if an I/O exception occurred, but not during the execution of the file
     *     creator (or the file creator was not executed)
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     file creator (or the file creator was not executed)
     */
    @NonNull
    public QueryResult createFile(
            @NonNull File outputFile,
            @NonNull Inputs inputs,
            @NonNull Callable<Void> fileCreator)
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

        File cacheEntryDir = getCacheEntryDir(inputs);
        File cachedFile = getCachedFile(cacheEntryDir);

        // Callable to create the output file
        Callable<Void> createOutputFile = () -> {
            // Delete the output file and create its parent directory first according to the
            // contract of this method
            FileUtils.deletePath(outputFile);
            Files.createParentDirs(outputFile);
            try {
                fileCreator.call();
            } catch (Exception exception) {
                throw new FileCreatorException(exception);
            }
            return null;
        };

        // Callable to copy the output file to the cached file
        Callable<Void> copyOutputFileToCachedFile = () -> {
            // Only copy if the output file exists as file creator is not required to always
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
            // Only copy if the cached file exist as file creator may not have produced an output
            // during the first time this cache is called on the given inputs
            if (cachedFile.exists()) {
                copyFileOrDirectory(cachedFile, outputFile);
            }
            return null;
        };

        // If the cache is hit, copy the cached file to the output file. The cached file should have
        // been guarded with a SHARED lock when this callable is invoked (see method
        // queryCacheEntry). Here, we guard the output file with an EXCLUSIVE lock so that other
        // threads/processes won't be able to write to the same output file at the same time.
        Callable<Void> actionIfCacheHit =
                () -> doLocked(outputFile, LockingType.EXCLUSIVE, copyCachedFileToOutputFile);

        // If the cache is missed or corrupted, create the output file and copy it to the cached
        // file. The cached file should have been guarded with an EXCLUSIVE lock when this callable
        // is invoked (see method queryCacheEntry). Here, we again guard the output file with an
        // EXCLUSIVE lock.
        Callable<Void> actionIfCacheMissedOrCorrupted =
                () -> doLocked(
                        outputFile,
                        LockingType.EXCLUSIVE, () -> {
                                createOutputFile.call();
                                copyOutputFileToCachedFile.call();
                                return null;
                        });

        return queryCacheEntry(
                inputs, cacheEntryDir, actionIfCacheHit, actionIfCacheMissedOrCorrupted);
    }

    /**
     * Creates the cached output file/directory via the given file creator callback function if it
     * does not yet exist. The returned result also contains the path to the cached file/directory.
     *
     * <p>To determine whether a cached file/directory exists, the client needs to provide all the
     * inputs that affect the creation of the output file/directory to the {@link Inputs} object.
     *
     * <p>If this method is called multiple times on the same list of inputs, the first call will
     * invoke the file creator to create the cached output file/directory (which is given as the
     * argument to the file creator callback function), and subsequent calls will return the cached
     * file/directory without invoking the file creator.
     *
     * <p>Note that the file creator is not required to always create an output. If the file creator
     * does not create an output on the first call, the cache will remember this result and return a
     * cached file that does not exist on subsequent calls.
     *
     * <p>Depending on whether there are other threads/processes concurrently accessing this cache
     * and the type of locking scope configured for this cache, this method may block until it is
     * allowed to continue.
     *
     * <p>Note that this method returns a cached file/directory that is located inside the cache
     * directory. To avoid corrupting the cache, the client must never write to the returned cached
     * file/directory (or any other files/directories inside the cache directory) without using the
     * cache's API. If the client wants to read the returned cached file/directory, it must ensure
     * that another thread (of the same or a different process) is not overwriting or deleting the
     * same cached file/directory at the same time.
     *
     * <p>WARNING: DO NOT use this method if the returned cached file/directory will be annotated
     * with Gradle's {@code @OutputFile} or {@code @OutputDirectory} annotations as it is undefined
     * behavior of Gradle incremental builds when multiple Gradle tasks have the same
     * output-annotated file/directory.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param fileCreator the callback function to create the output file/directory
     * @return the result of this query, which includes the path to the cached output file/directory
     * @throws ExecutionException if an exception occurred during the execution of the file creator
     * @throws IOException if an I/O exception occurred, but not during the execution of the file
     *     creator (or the file creator was not executed)
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     file creator (or the file creator was not executed)
     */
    @NonNull
    public QueryResult createFileInCacheIfAbsent(
            @NonNull Inputs inputs,
            @NonNull ExceptionConsumer<File> fileCreator)
            throws ExecutionException, IOException {
        File cacheEntryDir = getCacheEntryDir(inputs);
        File cachedFile = getCachedFile(cacheEntryDir);

        // If the cache is hit, do nothing. If the cache is missed or corrupted, create the cached
        // output file. The cached file should have been guarded with an EXCLUSIVE lock when this
        // callable is invoked (see method queryCacheEntry).
        Callable<Void> actionIfCacheMissedOrCorrupted = () -> {
            try {
                fileCreator.accept(cachedFile);
            } catch (Exception exception) {
                throw new FileCreatorException(exception);
            }
            return null;
        };

        QueryResult queryResult =
                queryCacheEntry(inputs, cacheEntryDir, () -> null, actionIfCacheMissedOrCorrupted);
        return new QueryResult(
                queryResult.getQueryEvent(),
                queryResult.getCauseOfCorruption(),
                Optional.of(cachedFile));
    }

    /**
     * Queries the cache entry to see if it exists (and whether it is corrupted) and invokes the
     * respective provided actions.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     * @param cacheEntryDir the cache entry directory
     * @param actionIfCacheHit the action to invoke if the cache entry exists and is not corrupted
     * @param actionIfCacheMissedOrCorrupted the action to invoke if the cache entry either does not
     *      exist or exists but is corrupted
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     * @throws ExecutionException if a {@link FileCreatorException} occurred
     * @throws IOException if an I/O exception occurred, and the exception was not wrapped by
     *     {@link FileCreatorException}
     * @throws RuntimeException if a runtime exception occurred, and the exception was not wrapped
     *     by {@link FileCreatorException}
     */
    @NonNull
    private QueryResult queryCacheEntry(
            @NonNull Inputs inputs,
            @NonNull File cacheEntryDir,
            @NonNull Callable<Void> actionIfCacheHit,
            @NonNull Callable<Void> actionIfCacheMissedOrCorrupted)
            throws ExecutionException, IOException {
        // In this method, we use two levels of locking: A SHARED lock on the cache directory and a
        // SHARED or EXCLUSIVE lock on the cache entry directory.
        try {
            // Guard the cache directory with a SHARED lock so that other threads/processes can read
            // or write to the cache at the same time but cannot delete the cache while it is being
            // read/written to. (Further locking within the cache will make sure multiple
            // threads/processes can read but cannot write to the same cache entry at the same
            // time.)
            return doLocked(mCacheDirectory, LockingType.SHARED, () -> {
                // Create (or recreate) the cache directory since it may not exist or might have
                // been deleted. The following method is thread-safe so it's okay to call it from
                // multiple threads (and processes).
                FileUtils.mkdirs(mCacheDirectory);

                // Guard the cache entry directory with a SHARED lock so that multiple
                // threads/processes can read it at the same time
                QueryResult queryResult = doLocked(cacheEntryDir, LockingType.SHARED, () -> {
                    QueryResult result = checkCacheEntry(inputs, cacheEntryDir);
                    // If the cache entry is HIT, run the given action
                    if (result.getQueryEvent().equals(QueryEvent.HIT)) {
                        mHits.incrementAndGet();
                        actionIfCacheHit.call();
                    }
                    return result;
                });
                // If the cache entry is HIT, return immediately
                if (queryResult.getQueryEvent().equals(QueryEvent.HIT)) {
                    return queryResult;
                }

                // Guard the cache entry directory with an EXCLUSIVE lock so that only one
                // thread/process can write to it
                return doLocked(cacheEntryDir, LockingType.EXCLUSIVE, () -> {
                    // Check the cache entry again as it might have been changed by another
                    // thread/process since the last time we checked it.
                    QueryResult result = checkCacheEntry(inputs, cacheEntryDir);

                    // If the cache entry is HIT, run the given action and return immediately
                    if (result.getQueryEvent().equals(QueryEvent.HIT)) {
                        mHits.incrementAndGet();
                        actionIfCacheHit.call();
                        return result;
                    }

                    // If the cache entry is CORRUPTED, delete the cache entry
                    if (result.getQueryEvent().equals(QueryEvent.CORRUPTED)) {
                        FileUtils.deletePath(cacheEntryDir);
                    }

                    // If the cache entry is MISSED or CORRUPTED, create or recreate the cache entry
                    mMisses.incrementAndGet();
                    FileUtils.mkdirs(cacheEntryDir);

                    actionIfCacheMissedOrCorrupted.call();

                    // Write the inputs to the inputs file for diagnostic purposes. We also use it
                    // to check whether a cache entry is corrupted or not.
                    Files.write(
                            inputs.toString(),
                            getInputsFile(cacheEntryDir),
                            StandardCharsets.UTF_8);

                    return result;
                });
            });
        } catch (ExecutionException exception) {
            // If FileCreatorException was thrown, it will be wrapped by a chain of execution
            // exceptions (due to nested doLocked() method calls), so let's iterate through the
            // causal chain and find out.
            for (Throwable cause : Throwables.getCausalChain(exception)) {
                if (cause instanceof FileCreatorException) {
                    // Externally, FileCreatorException is regarded as ExecutionException (see the
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
     * Checks the cache entry to see if it exists (and whether it is corrupted). If it is corrupted,
     * the returned result also contains its cause.
     *
     * <p>This method should usually return a {@link QueryEvent#HIT} result or a {@link
     * QueryEvent#MISSED} result. In some rare cases, it may return a {@link QueryEvent#CORRUPTED}
     * result if the cache entry is corrupted (e.g., if an error occurred while the cache entry was
     * being created, or the previous build was canceled abruptly by the user or by a power outage).
     *
     * @return the result of this query (which does not include the path to the cached output
     *     file/directory)
     */
    @NonNull
    private QueryResult checkCacheEntry(@NonNull Inputs inputs, @NonNull File cacheEntryDir) {
        if (!cacheEntryDir.exists()) {
            return new QueryResult(QueryEvent.MISSED);
        }

        // The absence of the inputs file indicates a corrupted cache entry
        File inputsFile = getInputsFile(cacheEntryDir);
        if (!inputsFile.exists()) {
            return new QueryResult(
                    QueryEvent.CORRUPTED,
                    new IllegalStateException(
                            String.format(
                                    "Inputs file '%s' does not exist",
                                    inputsFile.getAbsolutePath())));
        }

        // It is extremely unlikely that the contents of the inputs file are different from the
        // current inputs. If it happens, it may be because (1) some I/O error occurred when writing
        // to the inputs file, or (2) there is a hash collision (two lists of inputs are hashed into
        // the same key). In either case, we also report it as a corrupted cache entry.
        String inputsInCacheEntry;
        try {
            inputsInCacheEntry = Files.toString(inputsFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new QueryResult(QueryEvent.CORRUPTED, e);
        }
        if (!inputs.toString().equals(inputsInCacheEntry)) {
            return new QueryResult(
                    QueryEvent.CORRUPTED,
                    new IllegalStateException(
                            String.format(
                                    "Expected contents '%s' but found '%s' in inputs file '%s'",
                                    inputs.toString(),
                                    inputsInCacheEntry,
                                    inputsFile.getAbsolutePath())));
        }

        // If the inputs file is valid, report a HIT
        return new QueryResult(QueryEvent.HIT);
    }

    /**
     * Returns the path of the cache entry directory, which is a directory containing the actual
     * cached file/directory and possibly some other info files. The cache entry directory is unique
     * to the given list of inputs (different lists of inputs correspond to different cache entry
     * directories).
     */
    @NonNull
    private File getCacheEntryDir(@NonNull Inputs inputs) {
        return new File(mCacheDirectory, inputs.getKey());
    }

    /**
     * Returns the path of the cached output file/directory inside the cache entry directory.
     */
    @NonNull
    private File getCachedFile(@NonNull File cacheEntryDir) {
        return new File(cacheEntryDir, "output");
    }

    /**
     * Returns the path of an inputs file inside the cache entry directory, which will be used to
     * describe the inputs to an API call on the cache.
     */
    @NonNull
    private File getInputsFile(@NonNull File cacheEntryDir) {
        return new File(cacheEntryDir, "inputs");
    }

    /**
     * Returns the path of the cached output file/directory that is unique to the given list of
     * inputs (different lists of inputs correspond to different cached files/directories).
     *
     * <p>Note that this method returns the path only, the cached file/directory may or may not have
     * been created.
     *
     * <p>This method is typically used together with the
     * {@link #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)} method to get the path of the
     * cached file/directory in advance, before attempting to create it at a later time. The
     * returned path of this method is guaranteed to be the same as the returned path by that
     * method. The client calling this method should take precautions in handling the returned
     * cached file/directory; refer to the javadoc of
     * {@link #createFileInCacheIfAbsent(Inputs, ExceptionConsumer)} for more details.
     *
     * @param inputs all the inputs that affect the creation of the output file/directory
     */
    @NonNull
    public File getFileInCache(@NonNull Inputs inputs) {
        return getCachedFile(getCacheEntryDir(inputs));
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
     * @throws ExecutionException if an exception occurred during the execution of the action
     * @throws IOException if an I/O exception occurred, but not during the execution of the action
     * @throws RuntimeException if a runtime exception occurred, but not during the execution of the
     *     action
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

        // ReadWriteProcessLock will normalize the lock file's path so that the paths can be
        // correctly compared by equals(), we don't need to normalize it here.
        ReadWriteProcessLock readWriteProcessLock = new ReadWriteProcessLock(lockFile.toPath());
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
        // We normalize the lock file's path so that the paths can be correctly compared by equals()
        ReadWriteThreadLock readWriteThreadLock =
                new ReadWriteThreadLock(accessedFile.toPath().normalize());
        ReadWriteThreadLock.Lock lock =
                lockingType == LockingType.SHARED
                        ? readWriteThreadLock.readLock()
                        : readWriteThreadLock.writeLock();
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
     * Checked exception thrown when the file creator callback function aborts due to an {@link
     * Exception}. This class is a private sub-class of {@link ExecutionException} and is used to
     * distinguish itself from other execution exceptions thrown elsewhere.
     */
    @Immutable
    private static final class FileCreatorException extends ExecutionException {

        public FileCreatorException(@NonNull Exception exception) {
            super(exception);
        }
    }

    /**
     * List of input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory to the {@link Inputs} object, including a command
     * for the file creator callback function, the input files/directories and other input
     * parameters. A command identifies a file creator (which usually corresponds to a Gradle task)
     * and is used to separate the cached results of different file creators when they use the same
     * cache. If the client uses a different file creator, or changes the file creator's
     * implementation, then it needs to provide a new unique command. For input files/directories,
     * it is important to select a combination of properties that uniquely identifies an input
     * file/directory depending on the specific use case (common properties of a file/directory
     * include its path, name, hash, size, and timestamp; it is usually good practice to use both
     * the path and hash to identify an input file/directory).
     *
     * <p>When constructing the {@link Inputs} object, the client is required to provide the command
     * (via the {@link Inputs.Builder} constructor) and at least one other input parameter.
     *
     * <p>The input parameters have the order in which they were added to this {@code Inputs}
     * object. If a parameter with the same name exists, the parameter's value is overwritten
     * (retaining its previous order).
     *
     * <p>Note that if some inputs are missing, the cache may return a cached file/directory that is
     * incorrect. On the other hand, if some irrelevant inputs are included, the cache may create a
     * new cached file/directory even though an existing one can be used. In other words, missing
     * inputs affect correctness, and irrelevant inputs affect performance. Thus, the client needs
     * to consider carefully what to include and exclude in these inputs.
     */
    @Immutable
    public static final class Inputs {

        @NonNull private static final String COMMAND = "COMMAND";

        @NonNull private final Command command;

        @NonNull private final LinkedHashMap<String, String> parameters;

        /** Builder of {@link FileCache.Inputs}. */
        public static final class Builder {

            @NonNull private final Command command;

            @NonNull
            private final LinkedHashMap<String, String> parameters = Maps.newLinkedHashMap();

            /**
             * Creates a {@link Builder} instance to construct an {@link Inputs} object.
             *
             * @param command the command that identifies a file creator callback function (which
             *     usually corresponds to a Gradle task)
             */
            public Builder(@NonNull Command command) {
                this.command = command;
            }

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
                parameters.put(name, file.getPath());
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

                parameters.put(name, Files.hash(file, Hashing.sha1()).toString());
                return this;
            }

            /**
             * Adds an input parameter with a String value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder putString(@NonNull String name, @NonNull String value) {
                parameters.put(name, value);
                return this;
            }

            /**
             * Adds an input parameter with a Boolean value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder putBoolean(@NonNull String name, @NonNull boolean value) {
                parameters.put(name, String.valueOf(value));
                return this;
            }

            /**
             * Adds an input parameter with a Long value. If a parameter with the same name
             * exists, the parameter's value is overwritten.
             */
            public Builder putLong(@NonNull String name, @NonNull long value) {
                parameters.put(name, String.valueOf(value));
                return this;
            }

            /**
             * Builds an {@code Inputs} instance.
             *
             * @throws IllegalStateException if the inputs are empty
             */
            public Inputs build() {
                Preconditions.checkState(!parameters.isEmpty(), "Inputs must not be empty.");
                return new Inputs(this);
            }
        }

        private Inputs(@NonNull Builder builder) {
            command = builder.command;
            parameters = Maps.newLinkedHashMap(builder.parameters);
        }

        @Override
        @NonNull
        public String toString() {
            return COMMAND + "=" + command.name() + System.lineSeparator() +
                    Joiner.on(System.lineSeparator()).withKeyValueSeparator("=").join(parameters);
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

    /**
     * Command to be provided by the client when using {@link FileCache}. A command identifies a
     * file creator callback function (which usually corresponds to a Gradle task) and is used to
     * separate the cached results of different file creators when they use the same cache.
     */
    public enum Command {

        /** Command used for testing only. */
        TEST,

        /** The predex-library command. */
        PREDEX_LIBRARY,

        /** The prepare-library command. */
        PREPARE_LIBRARY
    }

    /**
     * The result of a cache query, which includes a {@link QueryEvent} indicating whether the cache
     * is hit, missed, or corrupted, a cause if the cache is corrupted, and an (optional) path to
     * the cached output file/directory.
     */
    @Immutable
    public static final class QueryResult {

        @NonNull private final QueryEvent queryEvent;

        @NonNull private final Optional<Throwable> causeOfCorruption;

        @NonNull private final Optional<File> cachedFile;

        /**
         * Creates a {@code QueryResult} instance.
         *
         * @param queryEvent the query event
         * @param causeOfCorruption a cause if the cache is corrupted, and empty otherwise
         * @param cachedFile the path to cached output file/directory, can be empty if the cache
         *     does not want to expose this information
         */
        QueryResult(
                @NonNull QueryEvent queryEvent,
                @NonNull Optional<Throwable> causeOfCorruption,
                @NonNull Optional<File> cachedFile) {
            Preconditions.checkState(
                    (queryEvent.equals(QueryEvent.CORRUPTED) && causeOfCorruption.isPresent())
                            || (!queryEvent.equals(QueryEvent.CORRUPTED)
                                    && !causeOfCorruption.isPresent()));

            this.queryEvent = queryEvent;
            this.causeOfCorruption = causeOfCorruption;
            this.cachedFile = cachedFile;
        }

        QueryResult(@NonNull QueryEvent queryEvent, @NonNull Throwable causeOfCorruption) {
            this(queryEvent, Optional.of(causeOfCorruption), Optional.empty());
        }

        QueryResult(@NonNull QueryEvent queryEvent) {
            this(queryEvent, Optional.empty(), Optional.empty());
        }

        /**
         * Returns the {@link QueryEvent} indicating whether the cache is hit, missed, or corrupted.
         */
        @NonNull
        public QueryEvent getQueryEvent() {
            return queryEvent;
        }

        /**
         * Returns a cause if the cache is corrupted, and empty otherwise.
         */
        @NonNull
        public Optional<Throwable> getCauseOfCorruption() {
            return causeOfCorruption;
        }

        /**
         * Returns the path to the cached output file/directory, can be empty if the cache does not
         * want to expose this information.
         */
        @NonNull
        public Optional<File> getCachedFile() {
            return cachedFile;
        }
    }

    /**
     * The event that happens when the client queries a cache entry: the cache entry may be hit,
     * missed, or corrupted.
     */
    public enum QueryEvent {

        /** The cache entry exists and is not corrupted. */
        HIT,

        /** The cache entry does not exist. */
        MISSED,

        /** The cache entry exists and is corrupted. */
        CORRUPTED;
    }
}
