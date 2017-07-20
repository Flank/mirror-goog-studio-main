/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.truth.DexClassSubject;
import com.google.common.io.Files;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


@RunWith(FilterableParameterized.class)
public class DataBindingIncrementalTest {

    @Rule
    public GradleTestProject project;

    private static final String EXPORT_INFO_TASK = ":dataBindingExportBuildInfoDebug";

    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";

    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";

    private static final String ACTIVITY_MAIN_JAVA
            = "src/main/java/android/databinding/testapp/MainActivity.java";

    @Parameterized.Parameters(name = "experimental_{0}")
    public static List<Object[]> parameters() {
        return Arrays.asList(
                new Object[][] {
                    {false}, {true},
                });
    }

    public DataBindingIncrementalTest(boolean experimental) {
        project =
                GradleTestProject.builder()
                        .fromTestProject("databindingIncremental")
                        .useExperimentalGradleVersion(experimental)
                        .create();
    }

    @Test
    public void compileWithoutChange() throws Exception {
        GradleBuildResult result = project.executor().run(EXPORT_INFO_TASK);
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        result = project.executor().run(EXPORT_INFO_TASK);
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
        assertRecompile();
    }

    @Test
    public void changeJavaCode() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_JAVA), 44, "return false;");
        GradleBuildResult result = project.executor().run(EXPORT_INFO_TASK);

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertRecompile();
    }

    @Test
    public void changeVariableName() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo2\" type=\"String\"/>");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 29,
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        DexClassSubject bindingClass =
                assertThat(project.getApk(DEBUG))
                        .hasMainDexFile()
                        .that()
                        .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                        .that();
        bindingClass.doesNotHaveMethod("setFoo");
        bindingClass.hasMethod("setFoo2");
        assertRecompile();
    }

    @Test
    public void addVariable() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .hasMethods("setFoo", "setFoo2");
        assertRecompile();
    }

    @Test
    public void addIdToView() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30,
                "android:id=\"@+id/myTextView\"");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .hasField("myTextView");

        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30, "");
        result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that()
                .doesNotHaveField("myTextView");
        assertRecompile();
    }

    @Test
    public void addNewLayoutFolderAndFile() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File landscapeActivity = new File(mainActivity
                .getParentFile().getParentFile(), "layout-land/activity_main.xml");
        landscapeActivity.getParentFile().mkdirs();
        Files.copy(mainActivity, landscapeActivity);
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass(
                        "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;");

        // delete and recompile
        assertThat(landscapeActivity.delete()).isTrue();
        result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(project.getApk(DEBUG))
                .doesNotContainClass(
                        "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;");
    }

    @Test
    public void addNewLayout() throws Exception {
        project.execute(EXPORT_INFO_TASK);
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        assertThat(
                        project.getIntermediateFile(
                                "data-binding-info", "debug", "activity2-layout.xml"))
                .doesNotExist();

        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        assertThat(
                        project.getIntermediateFile(
                                "data-binding-info", "debug", "activity2-layout.xml"))
                .exists();

        assertThat(project.getApk(DEBUG))
                .hasMainDexFile()
                .that()
                .containsClass("Landroid/databinding/testapp/databinding/Activity2Binding;")
                .that()
                .hasMethod("setFoo");
        assertRecompile();
    }

    @Test
    public void removeLayout() throws Exception {
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG))
                .containsClass("Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertThat(
                        project.getIntermediateFile(
                                "data-binding-info", "debug", "activity2-layout.xml"))
                .exists();
        assertThat(activity2.delete()).isTrue();
        project.execute("assembleDebug");
        assertThat(project.getApk(DEBUG))
                .doesNotContainClass("Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertThat(
                        project.getIntermediateFile(
                                "data-binding-info", "debug", "activity2-layout.xml"))
                .doesNotExist();
        assertRecompile();
    }

    @Test
    public void renameLayout() throws Exception {
        String activity2ClassName = "Landroid/databinding/testapp/databinding/Activity2Binding;";
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        assertThat(project.getApk(DEBUG)).containsClass(activity2ClassName);
        TestFileUtils.replaceLine(project.file("src/main/res/layout/activity2.xml"), 19,
                "<data class=\"MyCustomName\">");
        result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();

        assertThat(project.getApk(DEBUG)).doesNotContainClass(activity2ClassName);
        assertThat(project.getApk(DEBUG))
                .containsClass("Landroid/databinding/testapp/databinding/MyCustomName;");
        assertRecompile();
    }

    private void assertRecompile() throws Exception {
        GradleBuildResult result = project.executor().run(EXPORT_INFO_TASK);

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
    }
}
