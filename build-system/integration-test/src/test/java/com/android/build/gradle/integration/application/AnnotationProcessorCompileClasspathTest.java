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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test various scenarios with AnnotationProcessorOptions.includeCompileClasspath */
public class AnnotationProcessorCompileClasspathTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("butterknife").create();

    @Before
    public void setUp() throws IOException {
        // Remove dependencies block from build file.
        TestFileUtils.searchAndReplace(project.getBuildFile(), "(?s)dependencies \\{.*\\}", "");
    }

    @Test
    public void failWhenClasspathHasProcessor() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n" + "    compile 'com.jakewharton:butterknife:7.0.1'\n" + "}\n");
        GradleBuildResult result = project.executor().expectFailure().run("assembleDebug");
        assertThat(result.getFailureMessage())
                .contains("Annotation processors must be explicitly declared");
        assertThat(result.getFailureMessage()).contains("butterknife-7.0.1.jar");
    }

    @Test
    public void checkSuccessWithIncludeCompileClasspath() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.defaultConfig.javaCompileOptions.annotationProcessorOptions.includeCompileClasspath = true\n"
                        + "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n");
        project.executor().run("assembleDebug");
    }

    @Test
    public void checkSuccessWhenProcessorIsSpecified() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile 'com.jakewharton:butterknife:7.0.1'\n"
                        + "    annotationProcessor 'com.jakewharton:butterknife:7.0.1'\n"
                        + "}\n");
        project.executor().run("assembleDebug");
    }
}
