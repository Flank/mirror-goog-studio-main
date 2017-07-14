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

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.shrinker.ShrinkerTestUtils;
import com.android.testutils.apk.Apk;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for minifyLib. */
@RunWith(FilterableParameterized.class)
public class MinifyLibTest {
    @Parameterized.Parameters(name = "useProguard = {0}")
    public static Collection<Boolean> data() {
        return ImmutableList.of(true, false);
    }

    @Parameterized.Parameter(0)
    public Boolean useProguard;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minifyLib").create();

    @Test
    public void consumerProguardFile() throws Exception {
        if (!useProguard) {
            ShrinkerTestUtils.enableShrinker(project.getSubproject(":app"), "debug");
        }

        project.executor().run(":app:assembleDebug");
        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        TruthHelper.assertThatApk(apk).containsClass("Lcom/android/tests/basic/UnusedClass;");
    }

    @Test
    public void shrinkingTheLibrary() throws Exception {
        enableLibShrinking();

        GradleBuildResult result = project.executor().run(":app:assembleDebug");

        assertThat(result.getTask(":app:transformClassesAndResourcesWithProguardForDebug"))
                .wasExecuted();

        Apk apk = project.getSubproject(":app").getApk(DEBUG);
        assertThat(apk).containsClass("Lcom/android/tests/basic/StringProvider;");
        assertThat(apk).doesNotContainClass("Lcom/android/tests/basic/UnusedClass;");
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

        // Test with only androidTestCompile.  Replacing the compile dependency is fine because the
        // app in the test project don't actually reference the library class directly during
        // compile time.
        TestFileUtils.searchAndReplace(
                project.getSubproject(":app").getBuildFile(),
                "compile project\\(':lib'\\)",
                "androidTestCompile project\\(':lib'\\)");
        GradleBuildResult result = project.executor().run(":app:assembleAndroidTest");

        assertThat(result.getTask(":app:transformClassesAndResourcesWithProguardForDebug"))
                .wasExecuted();
        assertThat(
                        result.getTask(
                                ":app:transformClassesAndResourcesWithProguardForDebugAndroidTest"))
                .wasExecuted();

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
        config.delete();
        TestFileUtils.appendToFile(config, "");
        project.executor().run(":lib:assembleDebug");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.executeConnectedCheck();
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

        if (!useProguard) {
            ShrinkerTestUtils.enableShrinker(project.getSubproject(":lib"), "release");
        }
    }
}
