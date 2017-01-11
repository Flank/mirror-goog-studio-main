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
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.utils.FileCache;
import com.android.prefs.AndroidLocation;
import java.io.File;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Class that contains utility methods for working with the build cache.
 */
public final class BuildCacheUtils {

    @NonNull public static final String BUILD_CACHE_TROUBLESHOOTING_MESSAGE =
            "To troubleshoot the issue or learn how to disable the build cache,"
                    + " go to https://d.android.com/r/tools/build-cache.html.\n"
                    + "If you are unable to fix the issue,"
                    + " please file a bug at https://d.android.com/studio/report-bugs.html.";

    /**
     * Returns an {@link Optional} with a {@link FileCache} instance representing the build cache if
     * the build cache is enabled, or empty if it is disabled. If enabled, the build cache directory
     * is set to a user-defined directory, or a default directory if the user-defined directory is
     * not provided.
     *
     * @throws RuntimeException if the ".android" directory does not exist or the build cache cannot
     * be created
     */
    @NonNull
    public static Optional<FileCache> createBuildCacheIfEnabled(
            @NonNull AndroidGradleOptions androidGradleOptions) {
        // Use a default directory if the user-defined directory is not provided
        Supplier<File> defaultBuildCacheDirSupplier = () -> {
            try {
                return new File(AndroidLocation.getFolder(), "build-cache");
            } catch (AndroidLocation.AndroidLocationException e) {
                throw new RuntimeException(e);
            }
        };

        return doCreateBuildCacheIfEnabled(androidGradleOptions, defaultBuildCacheDirSupplier);
    }

    @VisibleForTesting
    @NonNull
    static Optional<FileCache> doCreateBuildCacheIfEnabled(
            @NonNull AndroidGradleOptions androidGradleOptions,
            @NonNull Supplier<File> defaultBuildCacheDirSupplier) {
        if (!androidGradleOptions.isBuildCacheEnabled()) {
            return Optional.empty();
        }

        File buildCacheDir =
                androidGradleOptions.getBuildCacheDir() != null
                        ? androidGradleOptions.getBuildCacheDir()
                        : defaultBuildCacheDirSupplier.get();
        try {
            return Optional.of(FileCache.getInstanceWithInterProcessLocking(buildCacheDir));
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format(
                            "Unable to create the build cache at '%1$s'.\n"
                                    + "%2$s",
                            buildCacheDir.getAbsolutePath(),
                            BUILD_CACHE_TROUBLESHOOTING_MESSAGE),
                    exception);
        }
    }
}
