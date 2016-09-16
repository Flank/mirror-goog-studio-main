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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for Jack plugins that can be run as part of the Jack pipeline.
 */
public class JackPluginsTest {

    @Rule
    public GradleTestProject mProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void checkCoverageAsPlugin() throws Exception {
        File coveragePlugin =
                SdkHelper.getBuildTool(
                        Revision.parseRevision(
                                GradleTestProject.UPCOMING_BUILD_TOOL_VERSION,
                                Revision.Precision.MICRO),
                        BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
        updateBuildFile(
                ImmutableList.of("com.android.jack.coverage.CodeCoverage"),
                ImmutableList.of(coveragePlugin));
        GradleBuildResult result = mProject.executor().run("assembleDebug");

        assertThat(result.getStdout()).contains("--plugin com.android.jack.coverage.CodeCoverage");
        assertThat(result.getStdout()).contains("--pluginpath " + coveragePlugin);
    }

    @Test
    public void checkPluginNamesWithoutPath() throws Exception {
        updateBuildFile(ImmutableList.of("Plugin1"), ImmutableList.of());

        GradleBuildResult result = mProject.executor().expectFailure().run("assembleDebug");

        assertThat(result.getException()).isNotNull();
        //noinspection ThrowableResultOfMethodCallIgnored - should not throw
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("finished with non-zero exit value");
        assertThat(result.getStderr()).contains("Plugin 'Plugin1' not found");
    }

    @Test
    public void checkPluginPathWithoutName() throws Exception {
        File coveragePlugin =
                SdkHelper.getBuildTool(
                        Revision.parseRevision(
                                GradleTestProject.UPCOMING_BUILD_TOOL_VERSION,
                                Revision.Precision.MICRO),
                        BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
        updateBuildFile(ImmutableList.of(), ImmutableList.of(coveragePlugin));

        mProject.executor().run("assembleDebug");
    }

    @Test
    public void checkNameDuplicates() throws Exception {
        File coveragePlugin =
                SdkHelper.getBuildTool(
                        Revision.parseRevision(
                                GradleTestProject.UPCOMING_BUILD_TOOL_VERSION,
                                Revision.Precision.MICRO),
                        BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
        updateBuildFile(
                ImmutableList.of(
                        "com.android.jack.coverage.CodeCoverage",
                        "com.android.jack.coverage.CodeCoverage"),
                ImmutableList.of(coveragePlugin));

        GradleBuildResult result = mProject.executor().run("assembleDebug");

        assertThat(result.getStdout()).contains("--plugin com.android.jack.coverage.CodeCoverage");
        assertThat(result.getStdout()).contains("--pluginpath " + coveragePlugin);
    }

    @Test
    public void checkPathDuplicates() throws Exception {
        File coveragePlugin =
                SdkHelper.getBuildTool(
                        Revision.parseRevision(
                                GradleTestProject.UPCOMING_BUILD_TOOL_VERSION,
                                Revision.Precision.MICRO),
                        BuildToolInfo.PathId.JACK_COVERAGE_PLUGIN);
        updateBuildFile(
                ImmutableList.of("com.android.jack.coverage.CodeCoverage"),
                ImmutableList.of(coveragePlugin, coveragePlugin));

        GradleBuildResult result = mProject.executor().run("assembleDebug");

        assertThat(result.getStdout()).contains("--plugin com.android.jack.coverage.CodeCoverage");
        assertThat(result.getStdout()).contains("--pluginpath " + coveragePlugin);
    }

    private void updateBuildFile(@NonNull List<String> pluginNames, @NonNull List<File> paths)
            throws IOException {
        String names =
                pluginNames.stream().map(m -> "'" + m + "'").collect(Collectors.joining(","));
        String deps = "";
        for (File p : paths) {
            deps += "jackPlugin files('" + p + "')\n";
        }
        TestFileUtils.appendToFile(
                mProject.getBuildFile(),
                "android {\n"
                        + "     buildToolsVersion '"
                        + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION + "'\n"
                        + "     defaultConfig {\n"
                        + "        jackOptions {\n"
                        + "            enabled true\n"
                        + "            jackInProcess false\n" // see http://b.android.com/222326
                        + "            pluginNames = [" + names + "]\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "     " + deps
                        + "}");
    }
}
