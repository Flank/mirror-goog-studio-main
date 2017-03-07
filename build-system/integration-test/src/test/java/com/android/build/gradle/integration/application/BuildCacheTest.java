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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import java.io.File;
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
    public void setUp() throws Exception {
        // Add a dependency on an external library (guava)
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\ndependencies {\n    compile 'com.google.guava:guava:18.0'\n}\n");
    }

    @Test
    public void testBuildCacheEnabled() throws Exception {
        File buildCacheDir = new File(project.getTestDir(), "build-cache");
        FileUtils.deletePath(buildCacheDir);

        RunGradleTasks executor =
                project.executor()
                        .withUseDexArchive(false)
                        .with(BooleanOption.ENABLE_BUILD_CACHE, true)
                        .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.getAbsolutePath());
        executor.run("clean", "assembleDebug");

        File preDexDir =
                FileUtils.join(project.getIntermediatesDir(), "transforms", "preDex", "debug");
        TransformOutputContent preDexContent = new TransformOutputContent(preDexDir);

        List<File> cachedEntryDirs =
                Arrays.stream(buildCacheDir.listFiles())
                        .filter(File::isDirectory) // Remove the lock files
                        .collect(Collectors.toList());

        assertThat(preDexContent).hasSize(2);
        assertThat(cachedEntryDirs).hasSize(1);

        // Check the timestamps of the guava library's pre-dexed file and the cached file to make
        // sure we actually copied one to the other and did not run pre-dexing twice to create the
        // two files
        File cachedGuavaDexFile = new File(cachedEntryDirs.get(0), "output");
        File guavaDexFile = null;
        File projectDexFile = null;
        for (SubStream subStream : preDexContent) {
            if (subStream.getName().contains("guava")) {
                guavaDexFile = preDexContent.getLocation(subStream);
            } else {
                projectDexFile = preDexContent.getLocation(subStream);
            }
        }

        Truth.assertThat(guavaDexFile).named("guava dex file from: " + preDexDir).isNotNull();
        Truth.assertThat(projectDexFile).named("project dex file from: " + preDexDir).isNotNull();

        long cachedGuavaTimestamp = cachedGuavaDexFile.lastModified();
        long projectTimestamp = projectDexFile.lastModified();

        assertThat(guavaDexFile).wasModifiedAt(cachedGuavaTimestamp);

        executor.run("clean", "assembleDebug");

        cachedEntryDirs =
                Arrays.stream(buildCacheDir.listFiles())
                        .filter(File::isDirectory) // Remove the lock files
                        .collect(Collectors.toList());
        assertThat(cachedEntryDirs).hasSize(1);

        preDexContent = new TransformOutputContent(preDexDir);
        assertThat(preDexContent).hasSize(2);

        // Assert that the cached file is unchanged and the guava library's pre-dexed file is copied
        // from the cache
        assertThat(cachedGuavaDexFile).wasModifiedAt(cachedGuavaTimestamp);
        assertThat(guavaDexFile).wasModifiedAt(cachedGuavaTimestamp);
        assertThat(projectDexFile).isNewerThan(projectTimestamp);

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
}
