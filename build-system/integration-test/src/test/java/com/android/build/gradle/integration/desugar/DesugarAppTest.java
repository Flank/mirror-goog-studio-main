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

package com.android.build.gradle.integration.desugar;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;

/** Tests use of Java 8 language in the application module. */
public class DesugarAppTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void noTaskIfNoJava8Set() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks())
                .doesNotContain(":transformClassesWithDesugarForDebug");

        assertThat(result.getNotUpToDateTasks()).doesNotContain(":extractJava8LangSupportJar");
    }

    @Test
    public void taskRunsIfJava8Set() throws IOException, InterruptedException {
        enableDesugar();
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithDesugarForDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":extractJava8LangSupportJar");
    }

    @Test
    public void supportsJava8() throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "class Data {",
                        "    public static void doLambda() {",
                        "        Runnable r = () -> { };",
                        "    }",
                        "}"));

        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks()).contains(":transformClassesWithDesugarForDebug");
        assertThatApk(project.getApk("debug")).containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void convertsJava8Dependency()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        List<String> classes = createJava8LibAndGetClasses();
        project.executor().run("assembleDebug");
        Apk apk = project.getApk("debug");
        for (String klass : classes) {
            assertThat(apk).containsClass("L" + klass.replaceAll("\\.", "/") + ";");
        }
    }

    @Test
    public void runsAfterJacoco() throws IOException, InterruptedException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true");
        TestFileUtils.addMethod(
                FileUtils.join(project.getMainSrcDir(), "com/example/helloworld/HelloWorld.java"),
                "Runnable r = () -> { };");

        project.executor().run("assembleDebug");
    }

    @Test
    public void testBuildCacheIntegration()
            throws IOException, InterruptedException, ProcessException {
        // regression test for - http://b.android.com/292762
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.testCoverageEnabled true\n"
                        + "\nandroid.defaultConfig.minSdkVersion 9\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}");

        project.executor().run("assembleDebug");
        project.executor().run("clean", "assembleDebug");
        assertThat(project.getApk("debug"))
                .containsClass("Landroid/support/v4/app/ActivityCompat;");
    }

    @Test
    public void testWithoutDexArchives()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.defaultConfig.minSdkVersion 24");

        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "interface Data {",
                        "    static void staticMethod() {",
                        "    }",
                        "    default void defaultMethod() {",
                        "    }",
                        "}"));

        project.executor().withUseDexArchive(false).run("assembleDebug");
        assertThat(project.getApk("debug")).containsClass("Lcom/example/helloworld/Data;");
    }

    @Test
    public void testWithoutDexArchivesNoPredexing()
            throws IOException, InterruptedException, ProcessException {
        enableDesugar();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.defaultConfig.minSdkVersion 24\n"
                        + "android.dexOptions.preDexLibraries false\n"
                        + "dependencies {\n"
                        + "    compile 'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}");

        Files.write(
                project.getMainSrcDir().toPath().resolve("com/example/helloworld/Data.java"),
                ImmutableList.of(
                        "package com.example.helloworld;",
                        "interface Data {",
                        "    static void staticMethod() {",
                        "    }",
                        "    default void defaultMethod() {",
                        "    }",
                        "}"));

        project.executor().withUseDexArchive(false).run("assembleDebug");
        assertThat(project.getApk("debug")).containsClass("Lcom/example/helloworld/Data;");
    }

    private void enableDesugar() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.compileOptions.sourceCompatibility 1.8\n"
                        + "android.compileOptions.targetCompatibility 1.8");
    }

    @NonNull
    private List<String> createJava8LibAndGetClasses() throws IOException {
        class Utility {
            public void lambdaMethod() {
                Runnable r = () -> {};
            }
        }
        Path lib = project.getTestDir().toPath().resolve("libs/my-lib.jar");
        Files.createDirectories(lib.getParent());

        String path = Utility.class.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path);
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(lib))) {
            ZipEntry entry = new ZipEntry(path);
            out.putNextEntry(entry);
            out.write(ByteStreams.toByteArray(in));
            out.closeEntry();
        }
        return ImmutableList.of(Utility.class.getName());
    }
}
