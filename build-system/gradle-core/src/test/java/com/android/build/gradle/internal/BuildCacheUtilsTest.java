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

import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.utils.FileCache;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Unit tests for {@link BuildCacheUtils}. */
public class BuildCacheUtilsTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @Test
    public void testCreateBuildCache_Enabled_DirectorySet() throws IOException {
        File buildCacheDirectory = testDir.newFolder();

        ProjectOptions options =
                new ProjectOptions(
                        ImmutableMap.of(
                                BooleanOption.ENABLE_BUILD_CACHE.getPropertyName(),
                                Boolean.TRUE,
                                StringOption.BUILD_CACHE_DIR.getPropertyName(),
                                buildCacheDirectory.toString()));

        FileCache buildCache =
                BuildCacheUtils.createBuildCacheIfEnabled(BuildCacheUtilsTest::file, options);

        assertThat(buildCache).isNotNull();
        assertThat(buildCache.getCacheDirectory()).isEqualTo(buildCacheDirectory);
    }

    @Test
    public void testCreateBuildCache_Enabled_DirectoryNotSet() throws IOException {
        File defaultBuildCacheDir = testDir.newFolder();

        ProjectOptions options =
                new ProjectOptions(
                        ImmutableMap.of(
                                BooleanOption.ENABLE_BUILD_CACHE.getPropertyName(), Boolean.TRUE));

        FileCache buildCache =
                BuildCacheUtils.doCreateBuildCacheIfEnabled(
                        BuildCacheUtilsTest::file, options, () -> defaultBuildCacheDir);

        assertThat(buildCache).isNotNull();
        assertThat(buildCache.getCacheDirectory()).isEqualTo(defaultBuildCacheDir);
    }

    @Test
    public void testCreateBuildCache_Disabled() {
        ProjectOptions options =
                new ProjectOptions(
                        ImmutableMap.of(
                                BooleanOption.ENABLE_BUILD_CACHE.getPropertyName(), Boolean.FALSE));
        FileCache buildCache =
                BuildCacheUtils.createBuildCacheIfEnabled(BuildCacheUtilsTest::file, options);

        assertThat(buildCache).isNull();
    }

    private static File file(Object object) {
        return new File(object.toString());
    }
}
