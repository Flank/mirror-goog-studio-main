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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

public class MergeResourcesTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @Test
    public void mergesRawWithLibraryWithOverride() throws Exception {

        /*
         * Set app to depend on library.
         */
        File appBuild = project.getSubproject("app").getBuildFile();
        TestFileUtils.appendToFile(
                appBuild,
                "dependencies { compile project(':library') }" + System.lineSeparator());

        /*
         * Create raw/me.raw in library and see that it comes out in the apk.
         *
         * It should also show up in build/intermediates/res/merged/debug/raw/me.raw
         */
        File libraryRaw =
                FileUtils.join(project.getTestDir(), "library", "src", "main", "res", "raw");
        FileUtils.mkdirs(libraryRaw);
        Files.write(new File(libraryRaw, "me.raw").toPath(), new byte[] { 0, 1, 2 });

        project.execute(":app:assembleDebug");

        assertThatApk(project.getSubproject("app").getApk("debug"))
                .containsFileWithContent("res/raw/me.raw", new byte[] { 0, 1, 2 });

        File inIntermediate = FileUtils.join(
                project.getSubproject("app").getTestDir(),
                "build",
                "intermediates",
                "res",
                "merged",
                "debug",
                "raw",
                "me.raw");
        File apUnderscore = FileUtils.join(
                project.getSubproject("app").getTestDir(),
                "build",
                "intermediates",
                "res",
                "resources-debug.ap_");
        assertThat(inIntermediate).contains(new byte[] { 0, 1, 2 });

        /*
         * Create raw/me.raw in application and see that it comes out in the apk, overriding the
         * library's.
         *
         * The change should also show up in build/intermediates/res/merged/debug/raw/me.raw
         */

        File appRaw = FileUtils.join(project.getTestDir(), "app", "src", "main", "res", "raw");
        FileUtils.mkdirs(appRaw);
        Files.write(new File(appRaw, "me.raw").toPath(), new byte[] { 3 });

        project.execute(":app:assembleDebug");

        assertThatApk(project.getSubproject("app").getApk("debug"))
                .containsFileWithContent("res/raw/me.raw", new byte[] { 3 });
        assertThat(inIntermediate).contains(new byte[] { 3 });
        long intermediateModified = inIntermediate.lastModified();
        long apUModified = apUnderscore.lastModified();
        long apkModified = project.getSubproject("app").getApk("debug").lastModified();

        /*
         * Now, modify the library's and check that nothing changed.
         */
        Files.write(new File(libraryRaw, "me.raw").toPath(), new byte[] { 0, 1, 2, 4 });

        project.execute(":app:assembleDebug");

        assertThatApk(project.getSubproject("app").getApk("debug"))
                .containsFileWithContent("res/raw/me.raw", new byte[] { 3 });
        assertThat(inIntermediate).wasModifiedAt(intermediateModified);
        assertThat(apUnderscore).wasModifiedAt(apUModified);
        assertThat(project.getSubproject("app").getApk("debug")).wasModifiedAt(apkModified);
    }
}
