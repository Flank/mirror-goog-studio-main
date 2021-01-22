/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding.incremental;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_TRIGGER;
import static com.android.build.gradle.internal.tasks.databinding.DataBindingTriggerTaskKt.DATA_BINDING_TRIGGER_CLASS;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.TestUtils;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.DexSubject;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration test to ensure correctness of incremental builds when data binding is used. */
@RunWith(FilterableParameterized.class)
public class DataBindingIncrementalTest {

    @Rule
    public GradleTestProject project;

    // Tasks
    private static final String TRIGGER_TASK = ":dataBindingTriggerDebug";
    private static final String KAPT_TASK = ":kaptDebugKotlin";
    private static final String COMPILE_JAVA_TASK = ":compileDebugJavaWithJavac";

    // Generated source files
    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";
    private static final String MAIN_ACTIVITY_BINDING_CLASS_IMPL =
            "Landroid/databinding/testapp/databinding/ActivityMainBindingImpl;";
    private static final String MAIN_ACTIVITY_2_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/Activity2Binding;";
    private static final String MAIN_ACTIVITY_2_BINDING_CLASS_IMPL =
            "Landroid/databinding/testapp/databinding/Activity2BindingImpl;";

    // Layout files
    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";

    // Source files
    private static final String ACTIVITY_MAIN_JAVA =
            "src/main/java/android/databinding/testapp/MainActivity.java";
    private static final String USER_JAVA = "src/main/java/android/databinding/testapp/User.java";

    private final boolean useAndroidX;
    private final boolean withKotlin;

    private final List<String> mainActivityBindingClasses;

    @Parameterized.Parameters(name = "useAndroidX_{0}_withKotlin_{1}")
    public static Iterable<Boolean[]> classNames() {
        return ImmutableList.of(
                new Boolean[] {false, false},
                new Boolean[] {true, false},
                // Test one scenario with Kotlin is probably enough (instead of two)
                new Boolean[] {true, true});
    }

    public DataBindingIncrementalTest(boolean useAndroidX, boolean withKotlin) {
        this.useAndroidX = useAndroidX;
        this.withKotlin = withKotlin;
        mainActivityBindingClasses =
                ImmutableList.of(MAIN_ACTIVITY_BINDING_CLASS, MAIN_ACTIVITY_BINDING_CLASS_IMPL);
        GradleTestProjectBuilder builder =
                GradleTestProject.builder()
                        .fromTestProject("databindingIncremental")
                        .addGradleProperties(
                                BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX)
                        .withKotlinGradlePlugin(withKotlin);

        project = builder.create();
    }

    @Before
    public void setUp() throws IOException {
        if (withKotlin) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(),
                    "apply plugin: 'com.android.application'",
                    "apply plugin: 'com.android.application'\n"
                            + "apply plugin: 'kotlin-android'\n"
                            + "apply plugin: 'kotlin-kapt'");
        }
    }

    private File getTriggerClass() {
        return new File(
                ArtifactTypeUtil.getOutputDir(DATA_BINDING_TRIGGER.INSTANCE, project.getBuildDir()),
                "debug/android/databinding/testapp/" + DATA_BINDING_TRIGGER_CLASS + ".java");
    }

    private File getGeneratedSourceDir() {
        return withKotlin
                ? new File(project.getGeneratedDir(), "source/kapt/debug")
                : new File(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.AP_GENERATED_SOURCES.INSTANCE,
                                project.getBuildDir()),
                        "debug/out");
    }

    private File getGeneratedSourceFile() {
        return new File(
                getGeneratedSourceDir(),
                "android/databinding/testapp/databinding/ActivityMainBindingImpl.java");
    }

    private File getLayoutInfoDir() {
        return new File(
                ArtifactTypeUtil.getOutputDir(
                        DATA_BINDING_LAYOUT_INFO_TYPE_MERGE.INSTANCE, project.getBuildDir()),
                "debug/out");
    }

    private File getLayoutInfoFile(String fileName) {
        return new File(getLayoutInfoDir(), fileName);
    }

    @Test
    public void compileWithoutChange() throws Exception {
        project.executor().run(TRIGGER_TASK);
        File infoClass = getTriggerClass();
        assertThat(infoClass).exists();
        String contents = FileUtils.readFileToString(infoClass, Charsets.UTF_8);
        project.executor().run(TRIGGER_TASK);
        assertThat(getTriggerClass()).hasContents(contents);
    }

    @Test
    public void changeIrrelevantJavaCode() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getTriggerClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        String infoFileContents = FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String sourceFileContents = FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        long sourceFileTimestamp = generatedSourceFile.lastModified();

        // Make an irrelevant change, ideally data binding should not be invoked. However, since
        // data binding does not yet fully support incrementality, the sources are currently
        // re-generated for now.
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_JAVA), "return true;", "return false;");
        GradleBuildResult result = project.executor().run(COMPILE_JAVA_TASK);

        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();
        String updatedInfoFileContents =
                FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String updatedSourceFileContents =
                FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        assertThat(updatedInfoFileContents).isEqualTo(infoFileContents);
        assertThat(updatedSourceFileContents).isEqualTo(sourceFileContents);

        assertThat(result.getTask(TRIGGER_TASK)).wasUpToDate();
        assertThat(result.getTask(COMPILE_JAVA_TASK)).didWork();

        TestUtils.waitForFileSystemTick();
        assertThat(generatedSourceFile).isNewerThan(sourceFileTimestamp);
    }

    @Test
    public void changeRelevantJavaCode() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getTriggerClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        String infoFileContents = FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String sourceFileContents = FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        long sourceFileTimestamp = generatedSourceFile.lastModified();

        // Make a relevant change, data binding should be invoked and the sources should be
        // re-generated.
        TestFileUtils.searchAndReplace(
                project.file(USER_JAVA), "return this.name;", "return name;");
        GradleBuildResult result = project.executor().run(COMPILE_JAVA_TASK);

        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();
        String updatedInfoFileContents =
                FileUtils.readFileToString(generatedInfoFile, Charsets.UTF_8);
        String updatedSourceFileContents =
                FileUtils.readFileToString(generatedSourceFile, Charsets.UTF_8);
        assertThat(updatedInfoFileContents).isEqualTo(infoFileContents);
        assertThat(updatedSourceFileContents).isEqualTo(sourceFileContents);

        assertThat(result.getTask(TRIGGER_TASK)).wasUpToDate();
        assertThat(result.getTask(COMPILE_JAVA_TASK)).didWork();

        TestUtils.waitForFileSystemTick();
        assertThat(generatedSourceFile).isNewerThan(sourceFileTimestamp);
    }

    @Test
    public void breakRelevantJavaCodeExpectFailure() throws Exception {
        // Compile fully the first time
        project.execute(COMPILE_JAVA_TASK);

        File generatedInfoFile = getTriggerClass();
        File generatedSourceFile = getGeneratedSourceFile();
        assertThat(generatedInfoFile).exists();
        assertThat(generatedSourceFile).exists();

        // Make a relevant change that breaks data binding, data binding should be invoked and
        // compilation should fail.
        TestFileUtils.searchAndReplace(
                project.file(USER_JAVA),
                "public String getName() {",
                "public String getFirstName() {");
        GradleBuildResult result = project.executor().expectFailure().run(COMPILE_JAVA_TASK);
        String stacktrace = Throwables.getStackTraceAsString(checkNotNull(result.getException()));

        if (withKotlin) {
            assertThat(result.getTask(KAPT_TASK)).failed();
        } else {
            assertThat(result.getTask(COMPILE_JAVA_TASK)).failed();
        }
        assertThat(stacktrace)
                .contains("Could not find accessor android.databinding.testapp.User.name");
    }

    @Test
    public void changeVariableName() throws Exception {
        project.execute(TRIGGER_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<variable name=\"foo\" type=\"String\"/>",
                "<variable name=\"foo2\" type=\"String\"/>");
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:text='@{foo + \" \" + foo}'",
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        project.executor().run("assembleDebug");

        for (String className : mainActivityBindingClasses) {
            DexClassSubject bindingClass =
                    assertThat(project.getApk(DEBUG))
                            .hasMainDexFile()
                            .that()
                            .containsClass(className)
                            .that();
            bindingClass.doesNotHaveMethod("setFoo");
            bindingClass.hasMethod("setFoo2");
        }
    }

    @Test
    public void addVariable() throws Exception {
        project.execute(TRIGGER_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<variable name=\"foo\" type=\"String\"/>",
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        project.executor().run("assembleDebug");

        for (String className : mainActivityBindingClasses) {
            assertThat(project.getApk(DEBUG))
                    .hasMainDexFile()
                    .that()
                    .containsClass(className)
                    .that()
                    .hasMethods("setFoo", "setFoo2");
        }
    }

    @Test
    public void addIdToView() throws Exception {
        project.execute(TRIGGER_TASK);
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:text='@{foo + \" \" + foo}'",
                "<TextView android:text='@{foo + \" \" + foo}'\n"
                        + "android:id=\"@+id/myTextView\"");
        project.executor().run("assembleDebug");

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .hasField("myTextView");

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS_IMPL)
                .that()
                .doesNotHaveField("myTextView");

        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML), "android:id=\"@+id/myTextView\"", "");
        project.executor().run("assembleDebug");

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .doesNotHaveField("myTextView");
    }

    @Test
    public void addNewLayoutFolderAndFile() throws Exception {
        String mainActivityBindingClassImpl =
                "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;";

        project.execute(TRIGGER_TASK);
        File mainActivity = new File(project.getProjectDir(), ACTIVITY_MAIN_XML);
        File landscapeActivity = new File(mainActivity
                .getParentFile().getParentFile(), "layout-land/activity_main.xml");
        assertThat(landscapeActivity.getParentFile().mkdirs()).isTrue();
        Files.copy(mainActivity, landscapeActivity);
        project.executor().run("assembleDebug");

        DexSubject apk = assertThat(project.getApk(DEBUG)).hasMainDexFile().that();
        apk.containsClass(MAIN_ACTIVITY_BINDING_CLASS);
        apk.containsClass(mainActivityBindingClassImpl);
        apk.containsClass(MAIN_ACTIVITY_BINDING_CLASS_IMPL);

        // delete and recompile
        assertThat(landscapeActivity.delete()).isTrue();
        project.executor().run("assembleDebug");
        assertThat(project.getApk(DEBUG)).doesNotContainClass(mainActivityBindingClassImpl);
        for (String className : mainActivityBindingClasses) {
            assertThat(project.getApk(DEBUG)).containsClass(className);
        }
    }

    @Test
    public void addNewLayout() throws Exception {
        project.execute(TRIGGER_TASK);
        File mainActivity = new File(project.getProjectDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        assertThat(getLayoutInfoFile("activity2-layout.xml")).doesNotExist();

        project.executor().run("assembleDebug");

        assertThat(getLayoutInfoFile("activity2-layout.xml")).exists();

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_2_BINDING_CLASS)
                .that()
                .hasMethod("setFoo");
        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL)
                .that()
                .hasMethod("setFoo");
    }

    @Test
    public void removeLayout() throws Exception {
        File mainActivity = new File(project.getProjectDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG)).containsClass(MAIN_ACTIVITY_2_BINDING_CLASS);
        assertThat(project.getApk(DEBUG)).containsClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL);

        assertThat(getLayoutInfoFile("activity2-layout.xml")).exists();
        assertThat(activity2.delete()).isTrue();
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG)).doesNotContainClass(MAIN_ACTIVITY_2_BINDING_CLASS);
        assertThat(project.getApk(DEBUG)).doesNotContainClass(MAIN_ACTIVITY_2_BINDING_CLASS_IMPL);
        assertThat(getLayoutInfoFile("activity2-layout.xml")).doesNotExist();
    }

    /** Regression test for bug 153711619. */
    @Test
    public void removeDataBindingFromLayout() throws Exception {
        // useAndroidX is not relevant to this test, so testing it when useAndroidX=true is enough
        Assume.assumeTrue(useAndroidX);

        File mainActivityLayout = new File(project.getProjectDir(), ACTIVITY_MAIN_XML);
        File mainActivityLayoutInfo = getLayoutInfoFile("activity_main-layout.xml");
        File mainActivityBinding = getGeneratedSourceFile();
        File activity2Layout = new File(mainActivityLayout.getParentFile(), "activity2.xml");
        File activity2LayoutInfo = getLayoutInfoFile("activity2-layout.xml");
        File activity2Binding =
                new File(
                        getGeneratedSourceDir(),
                        "android/databinding/testapp/databinding/Activity2BindingImpl.java");
        Files.copy(mainActivityLayout, activity2Layout);

        project.execute(COMPILE_JAVA_TASK);
        assertThat(mainActivityLayoutInfo).exists();
        assertThat(mainActivityBinding).exists();
        assertThat(activity2LayoutInfo).exists();
        assertThat(activity2Binding).exists();

        // Remove data binding constructs from the layout file
        FileUtils.write(activity2Layout, "<RelativeLayout />");

        // Expect that the corresponding layout info file and generated class file to be removed
        project.execute(COMPILE_JAVA_TASK);
        assertThat(mainActivityLayoutInfo).exists();
        assertThat(mainActivityBinding).exists();
        assertThat(activity2LayoutInfo).doesNotExist();
        assertThat(activity2Binding).doesNotExist();
    }

    @Test
    public void renameLayout() throws Exception {
        String activity3ClassName = "Landroid/databinding/testapp/databinding/Activity3Binding;";
        String activity3ClassNameImpl =
                "Landroid/databinding/testapp/databinding/Activity3BindingImpl;";
        File mainActivity = new File(project.getProjectDir(), ACTIVITY_MAIN_XML);
        File activity3 = new File(mainActivity.getParentFile(), "activity3.xml");
        Files.copy(mainActivity, activity3);
        project.executor().run("assembleDebug");

        File activity3DataBindingInfo = getLayoutInfoFile("activity3-layout.xml");
        assertThat(activity3DataBindingInfo).exists();
        long dataBindingInfoLastModified = activity3DataBindingInfo.lastModified();
        TestUtils.waitForFileSystemTick();

        assertThat(project.getApk(DEBUG)).containsClass(activity3ClassName);
        assertThat(project.getApk(DEBUG)).containsClass(activity3ClassNameImpl);

        // Modify the file.
        long activity3LayoutLastModified = activity3.lastModified();
        TestUtils.waitForFileSystemTick();
        TestFileUtils.searchAndReplace(activity3, "<data>", "<data class=\"MyCustomName\">");

        // Make sure that the file was actually modified.
        assertThat(activity3.lastModified()).isNotEqualTo(activity3LayoutLastModified);

        project.executor().run("assembleDebug");

        assertThat(activity3DataBindingInfo).exists();
        assertThat(activity3DataBindingInfo.lastModified())
                .isNotEqualTo(dataBindingInfoLastModified);

        assertThat(project.getApk(DEBUG)).doesNotContainClass(activity3ClassName);
        assertThat(project.getApk(DEBUG)).doesNotContainClass(activity3ClassNameImpl);

        String customName = "Landroid/databinding/testapp/databinding/MyCustomName;";
        String customNameImpl = "Landroid/databinding/testapp/databinding/MyCustomNameImpl;";
        assertThat(project.getApk(DEBUG)).containsClass(customName);
        assertThat(project.getApk(DEBUG)).containsClass(customNameImpl);
    }

    /** Regression test for bug 151860061. */
    @Test
    public void testErrorsDoNotPersistAfterGettingFixed() throws Exception {
        // Test on one scenario is enough
        Assume.assumeTrue(useAndroidX && withKotlin);

        // Create a layout file with duplicate IDs, expect the build to fail
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:text='@{foo + \" \" + foo}'",
                "<TextView android:id=\"@+id/duplicateId\" />\n"
                        + "<TextView android:id=\"@+id/duplicateId\" />\n"
                        + "<TextView android:text='@{foo + \" \" + foo}'");
        project.executor().expectFailure().run(COMPILE_JAVA_TASK);

        // Correct the layout file, expect the build to pass
        TestFileUtils.searchAndReplace(
                project.file(ACTIVITY_MAIN_XML),
                "<TextView android:id=\"@+id/duplicateId\" />",
                "");
        project.executor().run(COMPILE_JAVA_TASK);
    }
}
