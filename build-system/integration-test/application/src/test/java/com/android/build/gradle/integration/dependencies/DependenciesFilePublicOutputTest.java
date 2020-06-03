/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_BUILD_TOOL_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class DependenciesFilePublicOutputTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: \"com.android.application\"\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void testPublicOutputFilePresent() throws Exception {
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(), "android.includeDependencyInfoInApks=true");
        project.executor()
                .withConfigurationCaching(
                        BaseGradleExecutor.ConfigurationCaching.OFF) // b/146208910
                .run("clean", "assembleRelease");

        // Check public output of dependency information was created and stored.
        File outputDir = project.getOutputDir();
        assertThat(outputDir).isDirectory();
        assertThat(FileUtils.join(outputDir, "sdk-dependencies")).isDirectory();
        assertThat(FileUtils.join(outputDir, "sdk-dependencies", "release", "sdkDependencies.txt"))
                .exists();
        try (BufferedReader file =
                new BufferedReader(
                        new FileReader(
                                FileUtils.join(
                                        outputDir,
                                        "sdk-dependencies",
                                        "release",
                                        "sdkDependencies.txt")))) {
            assertEquals(
                    file.readLine(),
                    "# List of SDK dependencies of this app, this information is also included in an encrypted form in the APK.");
            assertEquals(
                    file.readLine(),
                    "# For more information visit: https://d.android.com/r/tools/dependency-metadata");
        }
    }

    @Test
    public void testPublicOutputFileAbsent_debugBuild() throws Exception {
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(), "android.includeDependencyInfoInApks=true");
        project.execute("clean", "assembleDebug");

        File outputDir = project.getOutputDir();
        assertThat(outputDir).isDirectory();
        assertThat(FileUtils.join(outputDir, "sdk-dependencies")).doesNotExist();
    }

    @Test
    public void testPublicOutputFileAbsent_flagOff() throws Exception {
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(), "android.includeDependencyInfoInApks=false");
        project.executor()
                .withConfigurationCaching(
                        BaseGradleExecutor.ConfigurationCaching.OFF) // b/146208910
                .run("clean", "assembleRelease");

        File outputDir = project.getOutputDir();
        assertThat(outputDir).isDirectory();
        assertThat(FileUtils.join(outputDir, "sdk-dependencies")).doesNotExist();
    }
}
