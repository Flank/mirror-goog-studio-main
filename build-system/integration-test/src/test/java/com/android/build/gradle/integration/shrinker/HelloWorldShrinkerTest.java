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

package com.android.build.gradle.integration.shrinker;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.build.gradle.integration.shrinker.ShrinkerTestUtils.checkShrinkerWasUsed;

import com.android.build.gradle.integration.common.category.DeviceTestsQuarantine;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Tests for integration of the new class shrinker with Gradle. */
public class HelloWorldShrinkerTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void enableShrinking() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  buildTypes.debug {\n"
                        + "    minifyEnabled true\n"
                        + "    useProguard false\n"
                        + "    proguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "  }\n"
                        + "}\n");
        addUtilityClass();
    }

    @Test
    public void checkApkIsCorrect() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
        File helloWorld = findHelloWorld();
        File utils = findUtils();

        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Utils;");

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/example/helloworld/Utils.java"),
                "test log entry",
                "CHANGE");

        project.execute("assembleDebug", "assembleDebugAndroidTest");
        checkShrinkerWasUsed(project);

        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Utils;");

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                "Utils.helper\\(\\);",
                "// onCreate");

        project.execute("assembleDebug", "assembleDebugAndroidTest");
        checkShrinkerWasUsed(project);

        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContainClass("Lcom/example/helloworld/Utils;");
        assertThat(helloWorld).isNewerThan(utils);

        FileUtils.delete(project.file("src/main/java/com/example/helloworld/Utils.java"));
        project.execute("assembleDebug", "assembleDebugAndroidTest");

        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .doesNotContainClass("Lcom/example/helloworld/Utils;");

        long timestamp = helloWorld.lastModified();
        addUtilityClass();
        project.execute("assembleDebug", "assembleDebugAndroidTest");

        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Lcom/example/helloworld/Utils;");

        assertThat(helloWorld).isNewerThan(timestamp);
        assertThat(utils).isNewerThan(timestamp);
    }

    @Test
    @Category(DeviceTestsQuarantine.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
        checkShrinkerWasUsed(project);

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/example/helloworld/Utils.java"),
                "test log entry",
                "CHANGE");

        project.executeConnectedCheck();
        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/Utils;");

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                "Utils.helper\\(\\);",
                "// onCreate");

        project.executeConnectedCheck();
        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .doesNotContainClass("Lcom/example/helloworld/Utils;");

        FileUtils.delete(project.file("src/main/java/com/example/helloworld/Utils.java"));
        project.executeConnectedCheck();
        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .doesNotContainClass("Lcom/example/helloworld/Utils;");

        addUtilityClass();
        project.executeConnectedCheck();

        checkShrinkerWasUsed(project);
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk((GradleTestProject.ApkType.DEBUG)))
                .containsClass("Lcom/example/helloworld/Utils;");
    }

    private File findHelloWorld() {
        return FileUtils.find(ShrinkerTestUtils.getShrinkerOutputDir(project), "HelloWorld.class")
                .get();
    }

    private File findUtils() {
        return FileUtils.find(ShrinkerTestUtils.getShrinkerOutputDir(project), "Utils.class").get();
    }

    private void addUtilityClass() throws Exception {
        Path utilsSource =
                project.getMainSrcDir()
                        .toPath()
                        .resolve("com")
                        .resolve("example")
                        .resolve("helloworld")
                        .resolve("Utils.java");
        Files.createDirectories(utilsSource.getParent());
        Files.write(
                utilsSource,
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "public class Utils {",
                        "  public static void helper() {",
                        "    android.util.Log.i(\"Utils\", \"test log entry\");",
                        "  }",
                        "}"));

        TestFileUtils.searchAndReplace(
                project.file("src/main/java/com/example/helloworld/HelloWorld.java"),
                "// onCreate",
                "Utils.helper();");
    }
}
