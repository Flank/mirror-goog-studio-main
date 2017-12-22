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

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.builder.utils.FileCache;
import com.android.testutils.TestUtils;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link BuildCacheUtils}. */
public class BuildCacheUtilsTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    // Note: Since the tested class uses a global object (BuildSession), we need to use fake plugin
    // versions to avoid potential conflicts with integration tests which could be running in
    // parallel.
    @NonNull private final String fakePluginVersion = "1.2.3";

    @Test
    public void testCreateBuildCache_DirectorySet() throws IOException {
        File buildCacheDir = testDir.newFolder();
        Function<Object, File> pathToFileResolver = (path) -> new File(path.toString());
        Supplier<File> defaultBuildCacheDirSupplier =
                () -> {
                    fail("This should not run");
                    return null;
                };

        FileCache buildCache =
                BuildCacheUtils.createBuildCache(
                        buildCacheDir.getPath(),
                        pathToFileResolver,
                        defaultBuildCacheDirSupplier,
                        fakePluginVersion);
        assertThat(buildCache.getCacheDirectory())
                .isEqualTo(new File(buildCacheDir, fakePluginVersion));
    }

    @Test
    public void testCreateBuildCache_DirectoryNotSet() throws IOException {
        File defaultBuildCacheDir = testDir.newFolder();
        Function<Object, File> pathToFileResolver =
                (path) -> {
                    fail("This should not run");
                    return null;
                };
        Supplier<File> defaultBuildCacheDirSupplier = () -> defaultBuildCacheDir;

        FileCache buildCache =
                BuildCacheUtils.createBuildCache(
                        null, pathToFileResolver, defaultBuildCacheDirSupplier, fakePluginVersion);
        assertThat(buildCache.getCacheDirectory())
                .isEqualTo(new File(defaultBuildCacheDir, fakePluginVersion));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testDeleteOldCacheEntries() throws Exception {
        File cacheDir = testDir.newFolder();
        FileCache fileCache = FileCache.getInstanceWithMultiProcessLocking(cacheDir);
        FileCache.Inputs inputs1 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo1")
                        .build();
        FileCache.Inputs inputs2 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo2")
                        .build();
        FileCache.Inputs inputs3 =
                new FileCache.Inputs.Builder(FileCache.Command.TEST)
                        .putString("input", "foo3")
                        .build();

        // Make the first cache entry look as if it was created 60 days ago
        fileCache.createFileInCacheIfAbsent(inputs1, (outputFile) -> {});
        File cacheEntryDir1 = fileCache.getFileInCache(inputs1).getParentFile();
        cacheEntryDir1.setLastModified(System.currentTimeMillis() - Duration.ofDays(60).toMillis());

        // Make the second cache entry look as if it was created 30 days ago
        fileCache.createFileInCacheIfAbsent(inputs2, (outputFile) -> {});
        File cacheEntryDir2 = fileCache.getFileInCache(inputs2).getParentFile();
        cacheEntryDir2.setLastModified(System.currentTimeMillis() - Duration.ofDays(30).toMillis());

        // Make the third cache entry without modifying its timestamp
        fileCache.createFileInCacheIfAbsent(inputs3, (outputFile) -> {});

        // Delete all the cache entries that are older than or as old as 31 days
        BuildCacheUtils.deleteOldCacheEntries(
                fileCache, Duration.ofDays(31), Duration.ofMinutes(1));

        // Check that only the first cache entry is deleted
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs2)).isTrue();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();

        // Delete all the cache entries that are older than or as old as 30 days. However, this
        // cache eviction will not run because the cache eviction interval is set to 1 minute, and
        // it has not been 1 minute since the last run.
        BuildCacheUtils.deleteOldCacheEntries(
                fileCache, Duration.ofDays(30), Duration.ofMinutes(1));

        // Check that the results are still the same as before
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs2)).isTrue();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();

        // Delete all the cache entries that are older than or as old as 30 days, this time with a
        // cache eviction interval of 1 millisecond, so this should run after the file system tick.
        TestUtils.waitForFileSystemTick();
        BuildCacheUtils.deleteOldCacheEntries(fileCache, Duration.ofDays(30), Duration.ofMillis(1));

        // Check that only the third cache entry is kept
        assertThat(cacheEntryDir1).doesNotExist();
        assertThat(cacheEntryDir2).doesNotExist();
        assertThat(fileCache.cacheEntryExists(inputs3)).isTrue();
    }
}
