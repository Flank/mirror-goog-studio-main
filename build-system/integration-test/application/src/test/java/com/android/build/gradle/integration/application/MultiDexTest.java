/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for multiDex. */
@RunWith(FilterableParameterized.class)
public class MultiDexTest {

    enum MainDexListTool {
        DX,
        D8,
        R8,
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Parameterized.Parameters(name = "mainDexListTool = {0}")
    public static Object[] data() {
        return MainDexListTool.values();
    }

    @Parameterized.Parameter public MainDexListTool tool;

    @Test
    public void checkNormalBuild() throws Exception {
        // D8/R8 main dex list tool has a better understanding which classes should be kept
        // so this test is overapproximation in D8 case.
        Assume.assumeTrue(tool == MainDexListTool.DX);
        checkNormalBuild(true);
    }

    @Test
    public void checkBuildWithoutKeepRuntimeAnnotatedClasses() throws Exception {
        checkNormalBuild(false);
    }

    @Test
    public void checkApplicationNameAdded() throws IOException, InterruptedException {
        // noinspection ResultOfMethodCallIgnored
        FileUtils.join(project.getTestDir(), "src/ics/AndroidManifest.xml").delete();
        executor().run("processIcsDebugManifest");
        assertThat(
                        FileUtils.join(
                                project.getTestDir(),
                                "build/intermediates/manifests/full/ics/debug/AndroidManifest.xml"))
                .contains("android:name=\"android.support.multidex.MultiDexApplication\"");
    }

    private void checkNormalBuild(boolean keepRuntimeAnnotatedClasses) throws Exception {

        if (!keepRuntimeAnnotatedClasses) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");
        }

        executor().run("assembleDebug", "assembleAndroidTest");

        List<String> mandatoryClasses =
                Lists.newArrayList("Lcom/android/tests/basic/MyAnnotation;");
        if (keepRuntimeAnnotatedClasses) {
            mandatoryClasses.add("Lcom/android/tests/basic/ClassWithRuntimeAnnotation;");
        }

        assertMainDexContains("debug", mandatoryClasses);

        // manually inspect the apk to ensure that the classes.dex that was created is the same
        // one in the apk. This tests that the packaging didn't rename the multiple dex files
        // around when we packaged them.
        List<File> allClassesDex =
                FileUtils.find(
                        project.getIntermediateFile("transforms"),
                        Pattern.compile("(dex|dexMerger)/ics/debug/.*/classes\\.dex"));
        assertThat(allClassesDex).hasSize(1);
        File classesDex = allClassesDex.get(0);

        assertThat(project.getApk("ics", "debug"))
                .containsFileWithContent("classes.dex", Files.readAllBytes(classesDex.toPath()));

        File classes2Dex = FileUtils.join(classesDex.getParentFile(), "classes2.dex");

        assertThat(project.getApk("ics", "debug"))
                .containsFileWithContent("classes2.dex", Files.readAllBytes(classes2Dex.toPath()));

        commonApkChecks("debug");

        assertThat(project.getTestApk("ics"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");
        assertThat(project.getTestApk("lollipop"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        // Both test APKs should contain a class from Junit.
        assertThat(project.getTestApk("ics")).containsClass("Lorg/junit/Assert;");
        assertThat(project.getTestApk("lollipop")).containsClass("Lorg/junit/Assert;");

        assertThat(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk("ics", "debug"))
                .containsClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkProguard() throws Exception {
        checkMinifiedBuild("proguard");
    }

    @Test
    public void checkShrinker() throws Exception {
        checkMinifiedBuild("shrinker");
    }

    public void checkMinifiedBuild(String buildType) throws Exception {
        executor().run(StringHelper.appendCapitalized("assemble", buildType));

        assertMainDexContains(buildType, ImmutableList.of());

        commonApkChecks(buildType);

        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkAdditionalParameters() throws Exception {
        Assume.assumeTrue(
                "Only DX main dex list supports additional parameters.",
                tool == MainDexListTool.DX);
        FileUtils.deletePath(
                FileUtils.join(
                        project.getTestDir(),
                        "src",
                        "main",
                        "java",
                        "com",
                        "android",
                        "tests",
                        "basic",
                        "manymethods"));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.dexOptions.additionalParameters = ['--minimal-main-dex']\n");

        GradleTaskExecutor executor = executor().withUseDexArchive(false);
        executor.run("assembleIcsDebug", "assembleIcsDebugAndroidTest");

        assertThat(project.getApk(ApkType.DEBUG, "ics"))
                .containsClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk(ApkType.DEBUG, "ics"))
                .doesNotContainMainClass("Lcom/android/tests/basic/NotUsed;");

        // Make sure --minimal-main-dex was not used for the test APK.
        assertThat(project.getTestApk("ics")).contains("classes.dex");
        assertThat(project.getTestApk("ics")).doesNotContain("classes2.dex");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.dexOptions.additionalParameters '--set-max-idx-number=10'\n");

        GradleBuildResult result =
                executor().expectFailure().withUseDexArchive(false).run("assembleIcsDebug");

        assertThat(result.getStderr()).contains("main dex capacity exceeded");
    }

    @Test
    public void checkNativeMultidexAndroidTest() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    androidTestCompile 'com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}");
        executor().run("assembleLollipopDebugAndroidTest");
        // it should contain 2 dex files, one for sources, one for the external lib
        assertThat(project.getTestApk("lollipop")).contains("classes.dex");
        assertThat(project.getTestApk("lollipop")).contains("classes2.dex");
    }

    @Test
    public void checkLegacyMultiDexAndroidTest()
            throws IOException, InterruptedException, ProcessException {
        executor().run("assembleIcsDebugAndroidTest");

        Apk testApk = project.getTestApk("ics");
        assertThat(testApk).contains("classes.dex");
        assertThat(testApk).contains("classes2.dex");
        assertThat(testApk).containsMainClass("Lcom/android/tests/basic/OtherActivityTest;");
    }

    private void commonApkChecks(String buildType) throws Exception {
        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .containsClass("Landroid/support/multidex/MultiDexApplication;");
        assertThat(project.getApk(ApkType.of(buildType, true), "lollipop"))
                .doesNotContainClass("Landroid/support/multidex/MultiDexApplication;");

        for (String flavor : ImmutableList.of("ics", "lollipop")) {
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Main;");
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Used;");
            assertThat(project.getApk(ApkType.of(buildType, true), flavor))
                    .containsClass("Lcom/android/tests/basic/Kept;");
        }
    }

    private void assertMainDexContains(
            @NonNull String buildType, @NonNull List<String> mandatoryClasses) throws Exception {
        Apk apk = project.getApk("ics", buildType);
        Dex mainDex = apk.getMainDexFile().orElseThrow(AssertionError::new);

        ImmutableSet<String> mainDexClasses = mainDex.getClasses().keySet();
        assertThat(mainDexClasses).contains("Landroid/support/multidex/MultiDexApplication;");

        Set<String> nonMultidexSupportClasses =
                mainDexClasses
                        .stream()
                        .filter(c -> !c.startsWith("Landroid/support/multidex"))
                        .collect(Collectors.toSet());
        assertThat(nonMultidexSupportClasses).containsExactlyElementsIn(mandatoryClasses);
    }

    @NonNull
    private GradleTaskExecutor executor() {
        return project.executor()
                .with(BooleanOption.ENABLE_D8_MAIN_DEX_LIST, tool == MainDexListTool.D8)
                .with(BooleanOption.ENABLE_R8, tool == MainDexListTool.R8);
    }
}
