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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests to verify we process Jack compilation output properly.
 */
@RunWith(FilterableParameterized.class)
public class JackCompilationOutputTest {

    @Parameters(name = "in_process = {0}")
    public static List<Boolean> jackOptions() {
        return ImmutableList.of(true, false);
    }

    @Parameter
    public boolean inProcess;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void updateBuildFile() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        jackOptions {\n"
                        + "            enabled true\n"
                        + "            jackInProcess " + inProcess + "\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkErrorsAndWarningsInOutput() throws Exception {
        File userJava = FileUtils.join(project.getMainSrcDir(), "testing", "User.java");
        FileUtils.mkdirs(userJava.getParentFile());
        Files.write(
                "package testing;\n"
                        + "import java.util.List;\n"
                        + "import java.util.ArrayList;\n"
                        + "public class User {\n"
                        + "    int age =;\n"
                        + "    List<String> names = new ArrayList();\n"
                        + "}\n",
                userJava,
                StandardCharsets.UTF_8);

        GradleBuildResult result =
                project.executor()
                        .withProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, "true")
                        .expectFailure()
                        .run("assembleDebug");

        assertThat(result.getStderr())
                .containsMatch(
                        Pattern.compile(
                                "^AGPBI.*error.*User.java.*\"startLine\":4.*",
                                Pattern.MULTILINE));
        assertThat(result.getStdout())
                .containsMatch(
                        Pattern.compile(
                                "^AGPBI.*warning.*User.java.*\"startLine\":5.*",
                                Pattern.MULTILINE));
    }

    @Test
    public void checkWarningsInOutput() throws Exception {
        File userJava = FileUtils.join(project.getMainSrcDir(), "testing", "User.java");
        FileUtils.mkdirs(userJava.getParentFile());
        Files.write(
                "package testing;\n"
                        + "import java.util.List;\n"
                        + "import java.util.ArrayList;\n"
                        + "public class User {\n"
                        + "    List<String> names = new ArrayList();\n"
                        + "}\n",
                userJava,
                StandardCharsets.UTF_8);

        GradleBuildResult result =
                project.executor()
                        .withProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, "true")
                        .run("assembleDebug");

        assertThat(result.getStdout())
                .containsMatch(
                        Pattern.compile(
                                "^AGPBI.*warning.*User.java.*\"startLine\":4.*",
                                Pattern.MULTILINE));
    }

    @Test
    public void checkLogLevels() throws Exception {
        GradleBuildResult basicRun = project.executor().run("clean", "assembleDebug");

        GradleBuildResult infoRun =
                project.executor().withArgument("--info").run("clean", "assembleDebug");

        GradleBuildResult debugRun =
                project.executor().withArgument("--debug").run("clean", "assembleDebug");

        // basic should be contained in both info and debug
        for (String s : Splitter.on(System.lineSeparator()).split(basicRun.getStderr())) {
            assertThat(infoRun.getStdout()).contains(s);
            assertThat(debugRun.getStdout()).contains(s);
        }

        // info should be contained in debug
        for (String s : Splitter.on(System.lineSeparator()).split(infoRun.getStderr())) {
            assertThat(debugRun.getStdout()).contains(s);
        }

        // length of basic < length info < length debug
        assertThat(basicRun.getStdout().length()).isLessThan(infoRun.getStdout().length());
        assertThat(infoRun.getStdout().length()).isLessThan(debugRun.getStdout().length());
    }
}
