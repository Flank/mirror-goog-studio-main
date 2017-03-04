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
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
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
    public void setUp() throws Exception {
        // Add a dependency on an external library (guava)
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\ndependencies {\n    compile 'com.google.guava:guava:18.0'\n}\n");
    }

    @Test
    public void testBuildCacheEnabled() throws Exception {
        Path buildCacheDir = Paths.get(project.getTestDir().getAbsolutePath(), "build-cache");
        Files.deleteIfExists(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .withUseDexArchive(false)
                        .with(BooleanOption.ENABLE_BUILD_CACHE, true)
                        .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.toString());
        executor.run("clean", "assembleDebug");

        Path preDexDir =
                Paths.get(project.getIntermediatesDir().getAbsolutePath(), "transforms", "preDex");
        List<Path> dexFiles =
                Files.walk(preDexDir).filter(Files::isRegularFile).collect(Collectors.toList());
        List<Path> cachedEntryDirs =
                Files.walk(buildCacheDir, 1)
                        .filter(Files::isDirectory)
                        .skip(1)
                        .collect(Collectors.toList());

        assertThat(dexFiles).hasSize(2);
        assertThat(cachedEntryDirs).hasSize(1);

        // Check the timestamps of the guava library's pre-dexed file and the cached file to make
        // sure we actually copied one to the other and did not run pre-dexing twice to create the
        // two files
        Path cachedGuavaDexFile = cachedEntryDirs.get(0).resolve("output");
        Path guavaDexFile;
        Path projectDexFile;
        if (dexFiles.get(0).getFileName().toString().contains("guava")) {
            guavaDexFile = dexFiles.get(0);
            projectDexFile = dexFiles.get(1);
        } else {
            guavaDexFile = dexFiles.get(1);
            projectDexFile = dexFiles.get(0);
        }
        FileTime cachedGuavaTimestamp = Files.getLastModifiedTime(cachedGuavaDexFile);
        FileTime projectTimestamp = Files.getLastModifiedTime(projectDexFile);

        assertThat(Files.getLastModifiedTime(guavaDexFile)).isEqualTo(cachedGuavaTimestamp);

        executor.run("clean", "assembleDebug");

        dexFiles = Files.walk(preDexDir).filter(Files::isRegularFile).collect(Collectors.toList());
        cachedEntryDirs =
                Files.walk(buildCacheDir, 1)
                        .filter(Files::isDirectory)
                        .skip(1)
                        .collect(Collectors.toList());
        assertThat(dexFiles).hasSize(2);
        assertThat(cachedEntryDirs).hasSize(1);

        // Assert that the cached file is unchanged and the guava library's pre-dexed file is copied
        // from the cache
        assertThat(Files.getLastModifiedTime(cachedGuavaDexFile)).isEqualTo(cachedGuavaTimestamp);
        assertThat(Files.getLastModifiedTime(guavaDexFile)).isEqualTo(cachedGuavaTimestamp);
        assertThat(Files.getLastModifiedTime(projectDexFile)).isGreaterThan(projectTimestamp);

        executor.run("cleanBuildCache");
        assertThat(buildCacheDir).doesNotExist();
    }

    @Test
    public void testProjectLevelCache() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.defaultConfig.minSdkVersion 13\n"
                        + "dependencies {\n"
                        + "    compile \"com.android.support:support-v13:${rootProject.supportLibVersion}\"\n"
                        + "}\n");
        File buildCacheDir = new File(project.getTestDir(), "build-cache");
        FileUtils.deletePath(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .with(BooleanOption.ENABLE_BUILD_CACHE, false)
                        .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.getAbsolutePath());

        executor.run("clean", "assembleDebug");

        // When improved dependency resolution is enabled, a project local cache is used.
        File cacheDir = FileUtils.join(project.file("build"), FD_INTERMEDIATES, "project-cache");
        assertThat(cacheDir).isDirectory();
        assertThat(buildCacheDir).doesNotExist();
    }

    @Test
    public void testBuildCacheDisabled() throws Exception {
        Path buildCacheDir = Paths.get(project.getTestDir().getAbsolutePath(), "build-cache");
        Files.deleteIfExists(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .with(BooleanOption.ENABLE_BUILD_CACHE, false)
                        .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.toString());

        executor.run("clean", "assembleDebug");
        assertThat(buildCacheDir).doesNotExist();

        GradleBuildResult result = executor.expectFailure().run("cleanBuildCache");
        assertNotNull(result.getException());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Task 'cleanBuildCache' not found in root project");
    }
}
