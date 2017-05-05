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
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.JavaVersion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for multiDex. */
@RunWith(FilterableParameterized.class)
public class MultiDexTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Rule public Adb adb = new Adb();

    @Parameterized.Parameters(name = "dexInProcess = {0}")
    public static List<Boolean> data() {
        return Lists.newArrayList(true, false);
    }

    @Parameterized.Parameter public boolean dexInProcess;

    @Before
    public void disableDexInProcess() throws Exception {
        if (!dexInProcess) {
            DexInProcessHelper.disableDexInProcess(project.getBuildFile());
        }
    }

    @Test
    public void checkNormalBuild() throws Exception {
        checkNormalBuild(true);
    }

    @Test
    public void checkBuildWithoutKeepRuntimeAnnotatedClasses() throws Exception {
        checkNormalBuild(false);
    }

    private void checkNormalBuild(boolean keepRuntimeAnnotatedClasses) throws Exception {

        if (!keepRuntimeAnnotatedClasses) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "\nandroid.dexOptions.keepRuntimeAnnotatedClasses false");
        }

        project.execute("assembleDebug", "assembleAndroidTest");

        // additional classes that will be found in the list, if build tools version
        // is less than Aapt.VERSION_FOR_MAIN_DEX_LIST
        List<String> nonMandatoryMainDexClasses =
                Lists.newArrayList(
                        "com/android/tests/basic/Used",
                        "com/android/tests/basic/DeadCode",
                        "com/android/tests/basic/Main",
                        "com/android/tests/basic/OtherActivity");

        if (JavaVersion.current().isJava8Compatible()) {
            // javac 1.8 puts the InnerClasses attribute from R to R$id inside classes that use
            // R$id, like Main. The main dex list builder picks it up from the constant pool.
            nonMandatoryMainDexClasses.addAll(
                    ImmutableList.of(
                            "com/android/tests/basic/R",
                            "com/android/tests/basic/R$id",
                            "com/android/tests/basic/R$layout"));
        }

        List<String> mandatoryClasses =
                Lists.newArrayList("android/support/multidex/MultiDexApplication",
                        "com/android/tests/basic/MyAnnotation");
        if (keepRuntimeAnnotatedClasses) {
            mandatoryClasses.add("com/android/tests/basic/ClassWithRuntimeAnnotation");
        }

        assertMainDexListContains(
                "debug",
                mandatoryClasses,
                nonMandatoryMainDexClasses);

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
                .containsFileWithContent("classes.dex", Files.toByteArray(classesDex));

        File classes2Dex = FileUtils.join(classesDex.getParentFile(), "classes2.dex");

        assertThat(project.getApk("ics", "debug"))
                .containsFileWithContent("classes2.dex", Files.toByteArray(classes2Dex));

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
        project.execute("assemble" + StringHelper.capitalize(buildType));

        assertMainDexListContains(
                buildType,
                ImmutableList.of("android/support/multidex/MultiDexApplication"),
                ImmutableList.of(
                        "com/android/tests/basic/Used",
                        "com/android/tests/basic/Main",
                        "com/android/tests/basic/OtherActivity"));

        commonApkChecks(buildType);

        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/NotUsed;");
        assertThat(project.getApk(ApkType.of(buildType, true), "ics"))
                .doesNotContainClass("Lcom/android/tests/basic/DeadCode;");
    }

    @Test
    public void checkAdditionalParameters() throws Exception {

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

        project.execute("assembleIcsDebug", "assembleIcsDebugAndroidTest");

        assertThat(
                project.getApk(ApkType.DEBUG, "ics")
                        .containsClass("Lcom/android/tests/basic/NotUsed;"));
        assertThat(project.getApk(ApkType.DEBUG, "ics"))
                .doesNotContainMainClass("Lcom/android/tests/basic/NotUsed;");

        // Make sure --minimal-main-dex was not used for the test APK.
        assertThat(project.getTestApk("ics")).contains("classes.dex");
        assertThat(project.getTestApk("ics")).doesNotContain("classes2.dex");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.dexOptions.additionalParameters '--set-max-idx-number=10'\n");

        // dexing with dex archives does not support additional parameters
        GradleBuildResult result =
                project.executor().expectFailure().withUseDexArchive(false).run("assembleIcsDebug");

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
        project.execute("assembleLollipopDebugAndroidTest");
        // it should contain 2 dex files, one for sources, one for the external lib
        assertThat(project.getTestApk("lollipop")).contains("classes.dex");
        assertThat(project.getTestApk("lollipop")).contains("classes2.dex");
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

    private void assertMainDexListContains(
            @NonNull String buildType,
            @NonNull List<String> mandatoryClasses,
            @NonNull List<String> permittedToBeInMainDexClasses)
            throws Exception {
        File listFile =
                FileUtils.join(
                        project.getIntermediatesDir(),
                        "multi-dex",
                        "ics",
                        buildType,
                        "maindexlist.txt");

        Set<String> lines = Files.readLines(listFile, Charsets.UTF_8)
                .stream()
                .filter(line -> !line.isEmpty())
                .map(line -> line.replace(".class", ""))
                .collect(Collectors.toSet());

        // MultiDexApplication needs to be there
        assertThat(lines).containsAllIn(mandatoryClasses);

        // it may contain only classes from the support library
        // Check that the main dex list only contains:
        //  - The multidex support libray
        //  - The mandatory classes
        //  - The permittedToBeInMainDex classes.
        Set<String> unwantedExtraClasses =
                lines.stream()
                        .filter(line -> !line.startsWith("android/support/multidex"))
                        .collect(Collectors.toSet());
        unwantedExtraClasses.removeAll(mandatoryClasses);
        unwantedExtraClasses.removeAll(permittedToBeInMainDexClasses);

        assertThat(unwantedExtraClasses).named("Unwanted classes in main dex").isEmpty();

    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.execute(
                "assembleIcsDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebug",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        project.execute("connectedCheck");
    }
}
