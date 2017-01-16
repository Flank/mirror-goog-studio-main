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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.utils.FileCache;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

/**
 * Unit tests for {@link BuildCacheUtils}.
 */
public class BuildCacheUtilsTest {

    @Rule public TemporaryFolder testDir = new TemporaryFolder();

    @Mock private AndroidGradleOptions androidGradleOptions;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testCreateBuildCache_Enabled_ValidDirectory() throws IOException {
        File buildCacheDirectory = testDir.newFolder();

        when(androidGradleOptions.isBuildCacheEnabled()).thenReturn(true);
        when(androidGradleOptions.getBuildCacheDir()).thenReturn(buildCacheDirectory);
        Optional<FileCache> buildCache =
                BuildCacheUtils.createBuildCacheIfEnabled(androidGradleOptions);

        assertThat(buildCache).isPresent();
        // We compare the actual build cache directory with buildCacheDirectory.getCanonicalFile()
        // since the build cache (FileCache) might have normalized the directory's path
        assertThat(buildCache.get().getCacheDirectory()).isEqualTo(
                buildCacheDirectory.getCanonicalFile());
    }

    @Test
    public void testCreateBuildCache_Enabled_DirectoryNotSet() throws Exception {
        File defaultBuildCacheDir = testDir.newFolder();

        when(androidGradleOptions.isBuildCacheEnabled()).thenReturn(true);
        when(androidGradleOptions.getBuildCacheDir()).thenReturn(null);
        Optional<FileCache> buildCache =
                BuildCacheUtils.doCreateBuildCacheIfEnabled(
                        androidGradleOptions, () -> defaultBuildCacheDir);

        assertThat(buildCache).isPresent();
        // We compare the actual build cache directory with buildCacheDirectory.getCanonicalFile()
        // since the build cache (FileCache) might have normalized the directory's path
        assertThat(buildCache.get().getCacheDirectory()).isEqualTo(
                defaultBuildCacheDir.getCanonicalFile());
    }

    @Test
    public void testCreateBuildCache_Enabled_InvalidDirectory() {
        when(androidGradleOptions.isBuildCacheEnabled()).thenReturn(true);
        when(androidGradleOptions.getBuildCacheDir()).thenReturn(new File("\0"));

        try {
            BuildCacheUtils.createBuildCacheIfEnabled(androidGradleOptions);
            fail("Expected RuntimeException");
        } catch (RuntimeException exception) {
            assertThat(exception.getMessage()).contains("Unable to create the build cache");
        }
    }

    @Test
    public void testCreateBuildCache_Disabled() {
        when(androidGradleOptions.isBuildCacheEnabled()).thenReturn(false);
        Optional<FileCache> buildCache =
                BuildCacheUtils.createBuildCacheIfEnabled(androidGradleOptions);

        assertThat(buildCache).isAbsent();
    }
}
