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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.ANDROIDTEST_DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.truth.ModelContainerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for minifyLib. */
public class MinifyLibTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minifyLib").create();

    @Test
    public void consumerProguardFile() throws Exception {
        project.executor().run(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        assertThatApk(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        assertThatApk(apk).containsClass("Lcom/android/tests/basic/UnusedClass;");
    }

    @Test
    public void checkDefaultRulesExtraction() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "\nandroid.buildTypes.debug.minifyEnabled true");
        project.executor().run(":app:assembleDebug");

        assertThat(project.getIntermediateFile("default_proguard_files/global")).doesNotExist();
        assertThat(
                        project.getSubproject("app")
                                .getIntermediateFile("default_proguard_files/global"))
                .exists();
    }

    @Test
    public void wrongConsumerProguardFile() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                "android {\n"
                        + "defaultConfig.consumerProguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "}\n");

        ModelContainer<AndroidProject> container =
                project.model()
                        .ignoreSyncIssues()
                        .fetchAndroidProjects();
        ModelContainerSubject.assertThat(container)
                .rootBuild()
                .project(":lib")
                .hasSingleError(SyncIssue.TYPE_GENERIC)
                .that()
                .hasMessageThatContains(
                        "proguard-android.txt should not be used as a consumer configuration file");
    }

    @Test
    public void shrinkingTheLibrary() throws Exception {
        enableLibShrinking();

        GradleBuildResult result = project.executor().run(":app:assembleDebug");

        assertThat(result.getTask(":app:minifyDebugWithR8")).didWork();

        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        assertThat(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        assertThat(apk).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;");
    }

    /** Regression test for b/171364505. */
    @Test
    public void shrinkingLibWithMultidex() throws Exception {
        enableLibShrinking();
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                ""
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        multiDexEnabled true\n"
                        + "    }\n"
                        + "}");
        project.executor().run(":lib:assembleDebug");
    }

    /**
     * Ensure androidTest compile uses consumer proguard files from library.
     *
     * <p>The library contains an unused method that reference a class in guava, and guava is not in
     * the runtime classpath. Library also contains a consumer proguard file which would ignore
     * undefined reference during proguard. The test will fail during proguard if androidTest is not
     * using the proguard file.
     */
    @Test
    public void androidTestWithShrinkedLibrary() throws Exception {
        enableLibShrinking();

        // Test with only androidTestImplementation.  Replacing the compile dependency is fine
        // because the
        // app in the test project don't actually reference the library class directly during
        // compile time.
        TestFileUtils.searchAndReplace(
                project.getSubproject(":app").getBuildFile(),
                "api project(':lib')",
                "androidTestImplementation project\\(':lib'\\)");
        GradleBuildResult result = project.executor().run(":app:assembleAndroidTest");

        assertThat(result.getTask(":app:minifyDebugWithR8")).didWork();
        assertThat(result.getTask(":app:minifyDebugAndroidTestWithR8")).didWork();

        Apk apk = project.getSubproject(":app").getApk(ANDROIDTEST_DEBUG);
        assertThat(apk).exists();
    }

    /**
     * Tests the edge case of a library with no classes (after shrinking). We should at least not
     * crash.
     */
    @Test
    public void shrinkingTheLibrary_noClasses() throws Exception {
        enableLibShrinking();
        // Remove the -keep rules.
        File config = project.getSubproject(":lib").file("config.pro");
        FileUtils.deleteIfExists(config);
        TestFileUtils.appendToFile(config, "");
        project.executor().run(":lib:assembleDebug");
    }

    private void enableLibShrinking() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                ""
                        + "android {\n"
                        + "    buildTypes.debug {\n"
                        + "        minifyEnabled true\n"
                        + "        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'config.pro'\n"
                        + "    }\n"
                        + "}");
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "android {\n"
                        + "    buildTypes.debug {\n"
                        + "        minifyEnabled true\n"
                        + "        proguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "    }\n"
                        + "}\n");
    }
}
