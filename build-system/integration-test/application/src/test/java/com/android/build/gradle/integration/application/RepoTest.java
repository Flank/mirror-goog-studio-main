/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration test for maven publishing with multiple projects. */
public class RepoTest {

    @ClassRule
    public static GradleTestProject app =
            GradleTestProject.builder()
                    .withName("app")
                    .fromTestProject("repo/app")
                    // maven-publish is incompatible
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .create();

    @ClassRule
    public static GradleTestProject baseLibrary =
            GradleTestProject.builder()
                    .withName("baseLibrary")
                    // maven-publish is incompatible
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .fromTestProject("repo/baseLibrary")
                    .create();

    @ClassRule
    public static GradleTestProject library =
            GradleTestProject.builder()
                    .withName("library")
                    // maven-publish is incompatible
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .fromTestProject("repo/library")
                    .create();

    @ClassRule
    public static GradleTestProject util =
            GradleTestProject.builder()
                    .withName("util")
                    .fromTestProject("repo/util")
                    // maven-publish is incompatible
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .create();

    @BeforeClass
    public static void setUp() throws IOException {
        // Clean testRepo
        File testRepo = new File(app.getProjectDir(), "../testrepo");
        if (testRepo.isDirectory()) {
            FileUtils.deleteDirectoryContents(testRepo);
        }
    }

    @AfterClass
    public static void cleanUp() {
        app = null;
        baseLibrary = null;
        library = null;
        util = null;
    }

    @Test
    public void repo() throws IOException, InterruptedException, ProcessException {
        // publish the libraries in the order needed to build each
        util.execute("clean", "publishJavaPublicationToMavenRepository");
        baseLibrary.execute("clean", "publish");
        library.execute("clean", "publish");

        // build should fail because app cannot choose between free/paid variant of the library
        app.executor().expectFailure().run("clean", "assembleDebug", "assembleRelease");

        // specify disambiguation rule for the free/paid variant of the library.
        TestFileUtils.appendToFile(
                app.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  defaultConfig {\n"
                        + "    missingDimensionStrategy 'price', 'free'\n"
                        + "  }\n"
                        + "\n}");
        app.executor().run("clean", "assembleDebug", "assembleRelease");

        Apk debugApk = app.getApk(GradleTestProject.ApkType.DEBUG);
        assertThatApk(debugApk)
                .containsClass("Lcom/example/android/multiproject/library/DebugFoo;");
        assertThatApk(debugApk)
                .doesNotContainClass("Lcom/example/android/multiproject/library/ReleaseFoo;");

        Apk releaseApk = app.getApk(GradleTestProject.ApkType.RELEASE);
        assertThatApk(releaseApk)
                .containsClass("Lcom/example/android/multiproject/library/ReleaseFoo;");
        assertThatApk(releaseApk)
                .doesNotContainClass("Lcom/example/android/multiproject/library/DebugFoo;");
    }
}
