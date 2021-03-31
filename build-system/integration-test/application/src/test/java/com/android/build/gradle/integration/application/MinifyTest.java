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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.Version;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.CodeShrinker;
import com.android.builder.model.SyncIssue;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.android.utils.Pair;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Assemble tests for minify. */
public class MinifyTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void model() throws Exception {
        AndroidProject model =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();

        CodeShrinker minifiedShrinker =
                AndroidProjectUtils.getVariantByName(model, "minified")
                        .getMainArtifact()
                        .getCodeShrinker();
        assertThat(minifiedShrinker).isEqualTo(CodeShrinker.R8);

        CodeShrinker debugShrinker =
                AndroidProjectUtils.getDebugVariant(model).getMainArtifact().getCodeShrinker();
        assertThat(debugShrinker).isNull();
    }

    @Test
    public void appApkIsMinified() throws Exception {
        GradleBuildResult result = project.executor().run("assembleMinified");
        try (Scanner stdout = result.getStdout()) {
            ScannerSubject.assertThat(stdout).doesNotContain("Note");
        }
        try (Scanner stdout = result.getStdout()) {
            ScannerSubject.assertThat(stdout).doesNotContain("duplicate");
        }

        Apk apk = project.getApk("minified");
        Set<String> allClasses = Sets.newHashSet();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(
                    dex.getClasses()
                            .keySet()
                            .stream()
                            .filter(
                                    c ->
                                            !c.startsWith("Lorg/jacoco")
                                                    && !c.equals("Lcom/vladium/emma/rt/RT;"))
                            .collect(Collectors.toSet()));
        }

        assertThat(allClasses)
                .containsExactly(
                        "La/a;",
                        "Lcom/android/tests/basic/Main;",
                        "Lcom/android/tests/basic/IndirectlyReferencedClass;");

        File defaultProguardFile =
                project.file(
                        "build/"
                                + AndroidProject.FD_INTERMEDIATES
                                + "/default_proguard_files/global"
                                + "/proguard-android.txt"
                                + "-"
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(defaultProguardFile).exists();

        assertThat(apk)
                .hasMainClass("Lcom/android/tests/basic/Main;")
                .that()
                // Make sure default ProGuard rules were applied.
                .hasMethod("handleOnClick");
        assertThat(project.file("build/outputs/mapping/minified/mapping.txt")).exists();
        assertThat(project.file("build/outputs/mapping/minified/usage.txt")).exists();
        assertThat(project.file("build/outputs/mapping/minified/seeds.txt")).exists();
        assertThat(project.file("build/outputs/mapping/minified/configuration.txt")).exists();
    }

    @Test
    public void appTestDefaultKeepAnnotations() throws Exception {
        String classContent =
                "package example;\n"
                        + "public class ToBeKept {\n"
                        + "  @android.support.annotation.Keep String field1;\n"
                        + "  @androidx.annotation.Keep String field2;\n"
                        + "  String field3;\n"
                        + "  @androidx.annotation.Keep void foo() { }\n"
                        + "  @android.support.annotation.Keep void baz() { }\n"
                        + "  void fab() { }\n"
                        + "}";
        Path toBeKept = project.getMainSrcDir().toPath().resolve("example/ToBeKept.java");
        Files.createDirectories(toBeKept.getParent());
        Files.write(toBeKept, classContent.getBytes());

        project.addUseAndroidXProperty();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "dependencies {\n"
                        + "    implementation 'com.android.support:support-annotations:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    implementation 'androidx.annotation:annotation:1.0.0'\n"
                        + "}");

        project.executor().run("assembleMinified");

        Apk minified = project.getApk(GradleTestProject.ApkType.of("minified", true));
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasField("field1");
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasField("field2");
        assertThat(minified).hasClass("Lexample/ToBeKept;").that().doesNotHaveField("field3");

        assertThat(minified).hasClass("Lexample/ToBeKept;").that().hasMethods("foo", "baz");

        assertThat(minified).hasClass("Lexample/ToBeKept;").that().doesNotHaveMethod("fab");
    }

    @Test
    public void appTestExtractedJarKeepRules() throws Exception {
        String classContent = "package example;\n" + "public class ToBeKept { }";
        Path toBeKept = project.getMainSrcDir().toPath().resolve("example/ToBeKept.java");
        Files.createDirectories(toBeKept.getParent());
        Files.write(toBeKept, classContent.getBytes());

        String classContent2 = "package example;\n" + "public class ToBeRemoved { }";
        Path toBeRemoved = project.getMainSrcDir().toPath().resolve("example/ToBeRemoved.java");
        Files.createDirectories(toBeRemoved.getParent());
        Files.write(toBeRemoved, classContent2.getBytes());

        File jarFile = temporaryFolder.newFile("libkeeprules.jar");
        String keepRule = "-keep class example.ToBeKept";
        String keepRuleToBeIgnored = "-keep class example.ToBeRemoved";

        TestInputsGenerator.writeJarWithTextEntries(
                jarFile.toPath(),
                Pair.of("META-INF/com.android.tools/r8/rules.pro", keepRule),
                Pair.of("META-INF/com.android.tools/proguard/rules.pro", keepRule),
                Pair.of("META-INF/proguard/rules.pro", keepRuleToBeIgnored));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "dependencies {\n"
                        + "    implementation files ('"
                        + FileUtils.escapeSystemDependentCharsIfNecessary(jarFile.getAbsolutePath())
                        + "')\n"
                        + "}");

        project.executor().run("assembleMinified");

        Apk minified = project.getApk(GradleTestProject.ApkType.of("minified", true));
        assertThat(minified).containsClass("Lexample/ToBeKept;");
        assertThat(minified).doesNotContainClass("Lexample/ToBeRemoved;");
    }

    @Test
    public void appTestExtractedLegacyJarKeepRules() throws Exception {
        String classContent = "package example;\n" + "public class ToBeKept { }";
        Path toBeKept = project.getMainSrcDir().toPath().resolve("example/ToBeKept.java");
        Files.createDirectories(toBeKept.getParent());
        Files.write(toBeKept, classContent.getBytes());

        String classContent2 = "package example;\n" + "public class ToBeRemoved { }";
        Path toBeRemoved = project.getMainSrcDir().toPath().resolve("example/ToBeRemoved.java");
        Files.createDirectories(toBeRemoved.getParent());
        Files.write(toBeRemoved, classContent2.getBytes());

        File jarFile = temporaryFolder.newFile("libkeeprules.jar");
        String keepRule = "-keep class example.ToBeKept";

        TestInputsGenerator.writeJarWithTextEntries(
                jarFile.toPath(), Pair.of("META-INF/proguard/rules.pro", keepRule));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "dependencies {\n"
                        + "   implementation files ('"
                        + FileUtils.escapeSystemDependentCharsIfNecessary(jarFile.getAbsolutePath())
                        + "')\n"
                        + "}");

        project.executor().run("assembleMinified");

        Apk minified = project.getApk(GradleTestProject.ApkType.of("minified", true));
        assertThat(minified).containsClass("Lexample/ToBeKept;");
        assertThat(minified).doesNotContainClass("Lexample/ToBeRemoved;");
    }

    @Test
    public void testApkIsNotMinified_butMappingsAreApplied() throws Exception {
        // Run just a single task, to make sure task dependencies are correct.
        project.executor().run("assembleMinifiedAndroidTest");

        GradleTestProject.ApkType testMinified =
                GradleTestProject.ApkType.of("minified", "androidTest", true);

        Apk apk = project.getApk(testMinified);
        assertThat(apk)
                .named("applies mapping from the tested app only to tested classes")
                .containsClass("Lcom/android/tests/basic/MainTest;");
        assertThat(apk)
                .named("should not shrink test-only classes")
                .containsClass("Lcom/android/tests/basic/UnusedTestClass;");
        assertThat(apk)
                .named("should not rename test classes")
                .containsClass("Lcom/android/tests/basic/UsedTestClass;");
        assertThat(apk).containsClass("Lcom/android/tests/basic/test/R;");

        assertThat(apk)
                .hasClass("Lcom/android/tests/basic/MainTest;")
                .that()
                .hasFieldWithType("stringProvider", "La/a;");
    }

    @Test
    public void testProguardOptimizedBuildsSuccessfully() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "getDefaultProguardFile('proguard-android.txt')",
                "getDefaultProguardFile('proguard-android-optimize.txt')");
        project.executor().run("assembleMinified");
    }

    @Test
    public void testJavaResourcesArePackaged() throws IOException, InterruptedException {
        Path javaRes = project.getProjectDir().toPath().resolve("src/main/resources/my_res.txt");
        Files.createDirectories(javaRes.getParent());
        Files.createFile(javaRes);
        project.executor().run("assembleMinified");
        assertThat(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .contains("my_res.txt");
    }

    @Test
    public void testAndroidTestIsNotUpToDate() throws IOException, InterruptedException {
        project.executor().run("assembleMinified", "assembleMinifiedAndroidTest");

        TestFileUtils.appendToFile(project.file("proguard-rules.pro"), "\n-keep class **");
        GradleBuildResult minifiedAndroidTest =
                project.executor().run("assembleMinifiedAndroidTest");

        assertThat(minifiedAndroidTest.findTask(":minifyMinifiedAndroidTestWithR8")).didWork();
    }

    @Test
    public void testProguardRuleForNativeMethods() throws Exception {
        TestFileUtils.appendToFile(project.file("proguard-rules.pro"), "\n-printconfiguration");
        GradleBuildResult result = project.executor().run("assembleMinified");
        try (Scanner stdout = result.getStdout()) {
            ScannerSubject.assertThat(stdout)
                    .contains("-keepclasseswithmembernames,includedescriptorclasses class *");
        }
    }
}
