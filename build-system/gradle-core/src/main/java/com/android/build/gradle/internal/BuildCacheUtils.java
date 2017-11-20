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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.Version;
import com.android.builder.utils.FileCache;
import com.android.builder.utils.SynchronizedFile;
import com.android.prefs.AndroidLocation;
import com.android.utils.FileUtils;
import com.android.utils.concurrency.ReadWriteProcessLock;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.gradle.api.Project;

/**
 * Class that contains utility methods for working with the build cache.
 */
public final class BuildCacheUtils {

    @NonNull public static final String BUILD_CACHE_TROUBLESHOOTING_MESSAGE =
            "To troubleshoot the issue or learn how to disable the build cache,"
                    + " go to https://d.android.com/r/tools/build-cache.html.\n"
                    + "If you are unable to fix the issue,"
                    + " please file a bug at https://d.android.com/studio/report-bugs.html.";

    /** The number of days that cache entries are kept in the build cache. */
    private static final long CACHE_ENTRY_DAYS_TO_LIVE = 30;

    /**
     * The number of days until the next cache eviction is performed. This is to avoid running cache
     * eviction too frequently (e.g., with every build).
     */
    private static final long DAYS_BETWEEN_CACHE_EVICTION_RUNS = 1;

    /**
     * Returns a {@link FileCache} instance representing the build cache if the build cache is
     * enabled, or null if it is disabled. If enabled, the build cache directory is set to a
     * user-defined directory, or a default directory if the user-defined directory is not provided.
     *
     * <p>Cache eviction may be performed in this method at regular intervals (see {@link
     * BuildCacheUtils#DAYS_BETWEEN_CACHE_EVICTION_RUNS}}).
     */
    @Nullable
    public static FileCache createBuildCacheIfEnabled(
            @NonNull Project project, @NonNull ProjectOptions projectOptions) {
        if (!projectOptions.get(BooleanOption.ENABLE_BUILD_CACHE)) {
            return null;
        }

        // Use a default directory if the user-defined directory is not provided
        Supplier<File> defaultBuildCacheDirSupplier = () -> {
            try {
                return new File(AndroidLocation.getFolder(), "build-cache");
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new RuntimeException(e);
            }
        };

        FileCache buildCache =
                createBuildCache(
                        projectOptions.get(StringOption.BUILD_CACHE_DIR),
                        project.getRootProject()::file,
                        defaultBuildCacheDirSupplier,
                        Version.ANDROID_GRADLE_PLUGIN_VERSION);

        // Get the shared directory containing the build caches for different plugin versions.
        // In AGP 3.0.x and earlier, this directory contains the cache entries directly.
        // In AGP 3.1.x and later, this directory contains one subdirectory for each plugin version,
        // which then contains the cache entries for that version.
        File sharedBuildCacheDir = buildCache.getCacheDirectory().getParentFile();

        // Normalize the path in case different paths point to the same directory, this is important
        // for the locking mechanism below to work reliably.
        File normalizedSharedBuildCacheDir;
        try {
            normalizedSharedBuildCacheDir = sharedBuildCacheDir.getCanonicalFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Lock the shared build cache directory now for reading and unlock it later at the end
        // of the build. This is to prevent the case where a plugin with version 3.0.x or
        // earlier is deleting the shared cache directory while a plugin with version 3.1.x or
        // later is using a private cache directory inside the shared cache directory.
        String actionGroup =
                BuildCacheUtils.class.getName() + "@" + normalizedSharedBuildCacheDir.getPath();
        BuildSessionImpl.getSingleton()
                .executeOnce(
                        actionGroup,
                        "lockSharedBuildCacheDir",
                        () -> {
                            // Since the file's path has been normalized, there is a 1-1
                            // correspondence between the cache directory and the lock file
                            File lockFile =
                                    SynchronizedFile.getLockFile(normalizedSharedBuildCacheDir);
                            ReadWriteProcessLock.Lock lock =
                                    new ReadWriteProcessLock(lockFile.toPath()).readLock();
                            try {
                                lock.lock();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            BuildSessionImpl.getSingleton()
                                    .executeOnceWhenBuildFinished(
                                            actionGroup,
                                            "unlockSharedBuildCacheDir",
                                            () -> {
                                                try {
                                                    lock.unlock();
                                                } catch (IOException e) {
                                                    throw new UncheckedIOException(e);
                                                }
                                            });
                        });

        // Run cache eviction at regular intervals
        deleteOldCacheEntries(
                buildCache,
                Duration.ofDays(CACHE_ENTRY_DAYS_TO_LIVE),
                Duration.ofDays(DAYS_BETWEEN_CACHE_EVICTION_RUNS));

        return buildCache;
    }

    @NonNull
    @VisibleForTesting
    static FileCache createBuildCache(
            @Nullable String buildCacheDir,
            @NonNull Function<Object, File> pathToFileResolver,
            @NonNull Supplier<File> defaultBuildCacheDirSupplier,
            @NonNull String pluginVersion) {
        File sharedBuildCacheDir =
                buildCacheDir != null
                        ? pathToFileResolver.apply(buildCacheDir)
                        : defaultBuildCacheDirSupplier.get();
        // The actual build cache directory for a given plugin version is
        // <shared-build-cache-dir>/<plugin-version>
        return FileCache.getInstanceWithMultiProcessLocking(
                new File(sharedBuildCacheDir, pluginVersion));
    }

    /**
     * Deletes all the cache entries that have been kept in the build cache for longer than or as
     * long as the specified life time, but only performs this action if this is the first cache
     * eviction or the specified interval has elapsed since the last cache eviction.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    static void deleteOldCacheEntries(
            @NonNull FileCache buildCache,
            @NonNull Duration cacheEntryLifeTime,
            @NonNull Duration cacheEvictionInterval) {
        // Use a marker file to record the last time cache eviction was run
        File markerFile = new File(buildCache.getCacheDirectory(), ".cache-eviction-marker");
        long lastEvictionTimestamp = markerFile.lastModified();

        if (lastEvictionTimestamp == 0 /* The marker file does not yet exist */
                || Duration.ofMillis(System.currentTimeMillis() - lastEvictionTimestamp)
                                .compareTo(cacheEvictionInterval)
                        >= 0) {
            // There could be a race condition here if another thread/process also reaches this
            // point, but that is Okay because the method call below is thread-safe and
            // process-safe, it's just that the code below will be executed more than once (which is
            // fine).
            buildCache.deleteOldCacheEntries(
                    System.currentTimeMillis() - cacheEntryLifeTime.toMillis());

            // Create the marker file if it does not yet exist
            FileUtils.mkdirs(markerFile.getParentFile());
            try {
                markerFile.createNewFile();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            markerFile.setLastModified(System.currentTimeMillis());
        }
    }
}
