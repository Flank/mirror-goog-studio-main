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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Integration test for build cache. */
public class BuildCacheTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws IOException {
        AssumeUtil.assumeNotUsingJack();
        // Add a dependency on an external library (guava)
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\ndependencies {\n    compile 'com.google.guava:guava:17.0'\n}\n");
    }

    @Test
    public void testBuildCacheEnabled() throws IOException {
        File buildCacheDir = new File(project.getTestDir(), "build-cache");
        FileUtils.deletePath(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .withProperty("android.enableBuildCache", "true")
                        .withProperty("android.buildCacheDir", buildCacheDir.getAbsolutePath());
        executor.run("clean", "assembleDebug");

        File preDexDir = FileUtils.join(project.getIntermediatesDir(), "pre-dexed", "debug");
        List<File> dexFiles = Arrays.asList(preDexDir.listFiles());
        List<File> cachedEntryDirs =
                Arrays.asList(buildCacheDir.listFiles())
                        .stream()
                        .filter(File::isDirectory) // Remove the lock files
                        .collect(Collectors.toList());

        assertThat(dexFiles).hasSize(2);
        assertThat(cachedEntryDirs).hasSize(1);

        // Check the timestamps of the guava library's pre-dexed file and the cached file to make
        // sure we actually copied one to the other and did not run pre-dexing twice to create the
        // two files
        File cachedGuavaDexFile = new File(cachedEntryDirs.get(0), "output");
        File guavaDexFile;
        File projectDexFile;
        if (dexFiles.get(0).getName().contains("guava")) {
            guavaDexFile = dexFiles.get(0);
            projectDexFile = dexFiles.get(1);
        } else {
            guavaDexFile = dexFiles.get(1);
            projectDexFile = dexFiles.get(0);
        }
        long cachedGuavaTimestamp = cachedGuavaDexFile.lastModified();
        long projectTimestamp = projectDexFile.lastModified();

        assertThat(guavaDexFile).wasModifiedAt(cachedGuavaTimestamp);

        executor.run("clean", "assembleDebug");

        cachedEntryDirs =
                Arrays.asList(buildCacheDir.listFiles())
                        .stream()
                        .filter(File::isDirectory) // Remove the lock files
                        .collect(Collectors.toList());
        assertThat(preDexDir.list()).hasLength(2);
        assertThat(cachedEntryDirs).hasSize(1);

        // Assert that the cached file is unchanged and the guava library's pre-dexed file is copied
        // from the cache
        assertThat(cachedGuavaDexFile).wasModifiedAt(cachedGuavaTimestamp);
        assertThat(guavaDexFile).wasModifiedAt(cachedGuavaTimestamp);
        assertThat(projectDexFile).isNewerThan(projectTimestamp);

        executor.run("cleanBuildCache");
        assertThat(buildCacheDir).doesNotExist();
    }

    @Test
    public void testBuildCacheDisabled() throws IOException {
        File buildCacheDir = new File(project.getTestDir(), "build-cache");
        FileUtils.deletePath(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .withProperty("android.enableBuildCache", "false")
                        .withProperty("android.buildCacheDir", buildCacheDir.getAbsolutePath());

        // Improved dependency resolution must be disabled if build cache is disabled.
        executor.withProperty(
                AndroidGradleOptions.PROPERTY_ENABLE_IMPROVED_DEPENDENCY_RESOLUTION, "false");

        executor.run("clean", "assembleDebug");
        assertThat(buildCacheDir).doesNotExist();

        GradleBuildResult result = executor.expectFailure().run("cleanBuildCache");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Task 'cleanBuildCache' not found in root project");
    }
}
