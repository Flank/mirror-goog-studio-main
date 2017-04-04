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

package com.android.build.gradle.integration.api;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.SdkUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** test to verify the hook to register custom pre-javac compilers */
@Category(SmokeTests.class)
public class BytecodeGenerationHooksTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("bytecodeGenerationHooks").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void buildApp() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "app:assembleDebug");

        // check that the app's dex file contains the App class.
        Apk apk = project.getSubproject("app").getApk("debug");
        assertThat(apk.getFile()).isFile();
        Optional<Dex> dex = apk.getMainDexFile();
        assertThat(dex).isPresent();
        //noinspection OptionalGetWithoutIsPresent
        assertThat(dex.get()).containsClasses("Lcom/example/bytecode/App;");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebug): ",
                true,
                "library/build/intermediates/bundles/default/classes.jar",
                "jar/build/libs/jar.jar");
    }

    @Test
    public void buildAppTest() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "app:assembleAndroidTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebugAndroidTest): ",
                true,
                "app/build/generated/bytecode/debug",
                "app/build/intermediates/classes/debug",
                "library/build/intermediates/bundles/default/classes.jar",
                "jar/build/libs/jar.jar");
    }

    @Test
    public void buildAppUnitTest() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "app:testDebugUnitTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebugUnitTest): ",
                false,
                "app/build/generated/bytecode/debug",
                "app/build/intermediates/classes/debug",
                "library/build/intermediates/bundles/default/classes.jar",
                "jar/build/libs/jar.jar");
    }

    @Test
    public void buildLibrary() throws IOException, InterruptedException {
        project.execute("clean", "lib:assembleDebug");
    }

    @Test
    public void buildLibTest() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "lib:assembleAndroidTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:library:generateBytecodeFordebugAndroidTest): ",
                true,
                "library/build/intermediates/bundles/debug/classes.jar",
                "library/build/generated/bytecode/debug");
    }

    @Test
    public void buildLibUnitTest() throws IOException, InterruptedException {
        project.execute("clean", "lib:testDebugUnitTest");
    }

    @Test
    public void buildTestApp() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "test:assembleDebug");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:test:generateBytecodeFordebug): ",
                true,
                "app/build/intermediates/classes-jar/debug/classes.jar",
                "jar/build/libs/jar.jar",
                "library/build/intermediates/bundles/default/classes.jar");
    }

    private static void checkDependencies(
            GradleBuildResult result, String prefix, boolean exactly, String... dependencies) {
        String stdout = result.getStdout();
        Iterable<String> stdoutlines = Splitter.on(SdkUtils.getLineSeparator()).omitEmptyStrings().split(stdout);
        List<String> lines = Lists.newArrayList(stdoutlines);

        lines =
                lines.stream()
                        .filter(s -> s.startsWith(prefix))
                        .map(s -> s.substring(prefix.length()))
                        .collect(Collectors.toList());

        File projectDir = project.getTestDir();

        List<String> deps =
                Arrays.stream(dependencies)
                        .map(
                                s ->
                                        new File(projectDir, s.replace('/', File.separatorChar))
                                                .getAbsolutePath())
                        .collect(Collectors.toList());

        if (exactly) {
            TruthHelper.assertThat(lines).containsExactlyElementsIn(deps);
        } else {
            TruthHelper.assertThat(lines).containsAllIn(deps);
        }
    }
}
