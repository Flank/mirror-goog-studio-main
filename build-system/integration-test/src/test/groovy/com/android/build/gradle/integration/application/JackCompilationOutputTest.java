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
import static org.junit.Assume.assumeTrue;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;

import org.gradle.api.JavaVersion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tests to verify we process Jack compilation output properly.
 */
public class JackCompilationOutputTest {

    @Rule
    public GradleTestProject sBasic = GradleTestProject.builder().withName("basic")
            .fromTestProject("basic").create();

    private static final List<String> JACK_OPTIONS = ImmutableList
            .of("-Pcom.android.build.gradle.integratonTest.useJack=true",
                    "-PCUSTOM_BUILDTOOLS=" + GradleTestProject.UPCOMING_BUILD_TOOL_VERSION);

    @Test
    public void checkErrorsAndWarningsInOutput() throws Exception {
        File main =
                FileUtils.join(
                        sBasic.getMainSrcDir(), "com", "android", "tests", "basic", "Main.java");
        TestFileUtils.addMethod(main,
                "java.util.List wrong =  new java.util.ArrayList();\n"
                        + "int i = 10 //missing semicolon");

        GradleBuildResult result = sBasic.executor().withArguments(JACK_OPTIONS).expectFailure()
                .run("assembleDebug");

        // something like - Main.java:15: error: Syntax error, insert ";"
        assertThat(result.getStderr())
                .containsMatch(
                        Pattern.compile(
                                "^.*basic/src/main/java/com/android/tests/basic/Main"
                                        + "\\.java:\\d+:\\s*error:.*", Pattern.MULTILINE));
        assertThat(result.getStdout())
                .containsMatch(
                        Pattern.compile(
                                "^.*basic/src/main/java/com/android/tests/basic/Main"
                                        + "\\.java:\\d+:\\s*warning:.*", Pattern.MULTILINE));
    }

    @Test
    public void checkWarningsInOutput() throws Exception {
        File main =
                FileUtils.join(
                        sBasic.getMainSrcDir(), "com", "android", "tests", "basic", "Main.java");
        TestFileUtils.addMethod(main,
                "java.util.List wrong =  new java.util.ArrayList();");

        GradleBuildResult result = sBasic.executor().withArguments(JACK_OPTIONS)
                .run("assembleDebug");

        // something like - Main.java:15: warning: Syntax warning
        assertThat(result.getStdout())
                .containsMatch(
                        Pattern.compile(
                                "^.*basic/src/main/java/com/android/tests/basic/Main"
                                        + "\\.java:\\d+:\\s*warning:.*", Pattern.MULTILINE));
    }
}
