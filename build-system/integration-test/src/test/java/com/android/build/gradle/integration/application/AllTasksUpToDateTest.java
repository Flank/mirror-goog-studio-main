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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.truth.Truth;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AllTasksUpToDateTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes {\n"
                        + "  release { minifyEnabled true }\n"
                        + "  shrinker { minifyEnabled true; useProguard false }\n"
                        + "}");
    }

    @Test
    public void allTasksUpToDate() throws Exception {
        project.execute("assemble");
        GradleBuildResult result = project.executor().run("assemble");
        Set<String> executedTasks =
                result.getNotUpToDateTasks()
                        .stream()
                        .filter(task -> !result.getTask(task).isSkipped())
                        .collect(Collectors.toSet());

        Truth.assertThat(executedTasks)
                // Known exceptions:
                .containsExactly(
                        // Validate signing has no outputs, so it's never up-to-date.
                        ":validateSigningDebug",
                        // Lint declares no outputs, so it's never up-to-date. It's probably for the
                        // better, because it's hard to declare all inputs (they include the SDK
                        // and contents of the Google maven repo).
                        ":lintVitalRelease",
                        ":lintVitalShrinker");
    }
}
