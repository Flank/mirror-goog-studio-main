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

package com.android.build.gradle.integration.common.utils;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class PerformanceTestProjects {

    public static void initializeAntennaPod(GradleTestProject mainProject) throws IOException {
        GradleTestProject project = mainProject.getSubproject("AntennaPod");

        Files.move(
                mainProject.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                project.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "classpath \"com.android.tools.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');

        StringBuilder localRepositoriesSnippet = new StringBuilder();
        for (Path repo : GradleTestProject.getLocalRepositories()) {
            localRepositoriesSnippet.append(GradleTestProject.mavenSnippet(repo));
        }

        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "jcenter\\(\\)", localRepositoriesSnippet.toString());

        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion = \".*\"",
                "buildToolsVersion = \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\" // Updated by test");

        List<String> subprojects =
                ImmutableList.of("AudioPlayer/library", "afollestad/commons", "afollestad/core");

        for (String subproject: subprojects) {
            TestFileUtils.searchAndReplace(
                    mainProject.getSubproject(subproject).getBuildFile(),
                    "buildToolsVersion \".*\"",
                    "buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                            + "\" // Updated by test");
        }

        // Update the support lib and fix resulting issue:
        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.getBuildFile(),
                        mainProject.file("afollestad/core/build.gradle"),
                        mainProject.file("afollestad/commons/build.gradle"));

        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchAndReplace(
                    buildFile,
                    " 23",
                    " " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION);

            TestFileUtils.searchAndReplace(
                    buildFile,
                    "23.1.1",
                    GradleTestProject.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchAndReplace(
                mainProject.file("afollestad/core/src/main/res/values-v11/styles.xml"),
                "abc_ic_ab_back_mtrl_am_alpha",
                "abc_ic_ab_back_material");
    }

}
