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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.truth.DexClassSubject;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;


@RunWith(FilterableParameterized.class)
public class DataBindingIncrementalTest {

    @Rule
    public GradleTestProject project;

    private static final String EXPORT_INFO_TASK = ":dataBindingExportBuildInfoDebug";

    private static final String PROCESS_LAYOUTS_TASK = ":dataBindingProcessLayoutsDebug";

    private static final String MAIN_ACTIVITY_BINDING_CLASS =
            "Landroid/databinding/testapp/databinding/ActivityMainBinding;";

    private static final String ACTIVITY_MAIN_XML = "src/main/res/layout/activity_main.xml";

    private static final String ACTIVITY_MAIN_JAVA
            = "src/main/java/android/databinding/testapp/MainActivity.java";

    @Parameterized.Parameters(name = "experimental_{0} useJack_{1}")
    public static List<Object[]> parameters() {
        List<Object[]> options = new ArrayList<>();
        for (int i = 0 ; i < 4; i ++) {
            options.add(new Object[]{
                    (i & 1) != 0, (i & 2) != 0
            });
        }
        return options;
    }

    public DataBindingIncrementalTest(boolean experimental, boolean useJack) {
        project = GradleTestProject.builder()
                .fromTestProject("databindingIncremental")
                .useExperimentalGradleVersion(experimental)
                .withJack(useJack)
                .withBuildToolsVersion(
                        useJack ? GradleTestProject.UPCOMING_BUILD_TOOL_VERSION : null)
                .create();
    }

    @Test
    public void compileWithoutChange() throws UnsupportedEncodingException {
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasUpToDate();
        assertRecompile();
    }

    @Test
    public void changeJavaCode() throws IOException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_JAVA), 44, "return false;");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasUpToDate();
        assertRecompile();
    }

    @Test
    public void changeVariableName()
            throws IOException, ProcessException, ParserConfigurationException, SAXException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo2\" type=\"String\"/>");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 29,
                "<TextView android:text='@{foo2 + \" \" + foo2}'");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        DexClassSubject bindingClass = assertThat(project.getApk("debug")).hasMainDexFile()
                .that().containsClass(MAIN_ACTIVITY_BINDING_CLASS).that();
        bindingClass.doesNotHaveMethod("setFoo");
        bindingClass.hasMethod("setFoo2");
        assertRecompile();
    }

    @Test
    public void addVariable()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 20,
                "<variable name=\"foo\" type=\"String\"/><variable name=\"foo2\" type=\"String\"/>");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();
        assertThat(project.getApk("debug")).hasMainDexFile()
                .that().containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().hasMethods("setFoo", "setFoo2");
        assertRecompile();
    }

    @Test
    public void addIdToView()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30,
                "android:id=\"@+id/myTextView\"");
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        assertThat(project.getApk("debug")).hasMainDexFile()
                .that().containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().hasField("myTextView");

        TestFileUtils.replaceLine(project.file(ACTIVITY_MAIN_XML), 30, "");
        result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();
        assertThat(project.getApk("debug")).hasMainDexFile()
                .that().containsClass(MAIN_ACTIVITY_BINDING_CLASS)
                .that().doesNotHaveField("myTextView");
        assertRecompile();
    }

    @Test
    public void addNewLayoutFolderAndFile() throws IOException, ProcessException {
        project.execute("assembleDebug");
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File landscapeActivity = new File(mainActivity
                .getParentFile().getParentFile(), "layout-land/activity_main.xml");
        landscapeActivity.getParentFile().mkdirs();
        Files.copy(mainActivity, landscapeActivity);
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        assertThat(project.getApk("debug")).hasMainDexFile().that().containsClass(
                "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;");

        // delete and recompile
        assertThat(landscapeActivity.delete()).isTrue();
        result = project.executor().run("assembleDebug");
        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();
        assertThat(project.getApk("debug")).doesNotContainClass(
                "Landroid/databinding/testapp/databinding/ActivityMainBindingLandImpl;");
    }

    @Test
    public void addNewLayout()
            throws IOException, ProcessException, SAXException, ParserConfigurationException {
        project.execute("assembleDebug");
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        assertThat(project.getApk("debug")).hasMainDexFile()
                .that().containsClass("Landroid/databinding/testapp/databinding/Activity2Binding;")
                .that().hasMethod("setFoo");
        assertRecompile();
    }

    @Test
    public void removeLayout() throws IOException, ProcessException {
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertThat(activity2.delete()).isTrue();
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).doesNotContainClass(
                "Landroid/databinding/testapp/databinding/Activity2Binding;");
        assertRecompile();
    }

    @Test
    public void renameLayout() throws IOException, ProcessException {
        String activity2ClassName = "Landroid/databinding/testapp/databinding/Activity2Binding;";
        File mainActivity = new File(project.getTestDir(), ACTIVITY_MAIN_XML);
        File activity2 = new File(mainActivity.getParentFile(), "activity2.xml");
        Files.copy(mainActivity, activity2);
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        assertThat(project.getApk("debug")).containsClass(
                activity2ClassName);
        TestFileUtils.replaceLine(project.file("src/main/res/layout/activity2.xml"), 19,
                "<data class=\"MyCustomName\">");
        result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasNotUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasNotUpToDate();

        assertThat(project.getApk("debug")).doesNotContainClass(
                activity2ClassName);
        assertThat(project.getApk("debug")).containsClass(
                "Landroid/databinding/testapp/databinding/MyCustomName;");
        assertRecompile();
    }

    private void assertRecompile() {
        GradleBuildResult result = project.executor().run("assembleDebug");

        assertThat(result.getTask(EXPORT_INFO_TASK)).wasUpToDate();
        assertThat(result.getTask(PROCESS_LAYOUTS_TASK)).wasUpToDate();
    }
}
