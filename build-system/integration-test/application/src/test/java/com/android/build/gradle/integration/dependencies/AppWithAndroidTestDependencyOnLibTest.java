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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * App with androidTestCompile dependency on a library that share the same dependencies on a second
 * library.
 */
public class AppWithAndroidTestDependencyOnLibTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestCompile project(\":library\")\n"
                        + "    implementation 'com.android.support.constraint:constraint-layout:1.0.2'"
                        + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion 15\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    implementation 'com.android.support.constraint:constraint-layout:1.0.2'"
                        + "}\n");

        Files.write(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<android.support.constraint.ConstraintLayout "
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                        + "    android:orientation=\"horizontal\"\n"
                        + "    android:layout_width=\"fill_parent\"\n"
                        + "    android:layout_height=\"fill_parent\">\n"
                        + "\n"
                        + "    <TextView\n"
                        + "    android:layout_width=\"wrap_content\"\n"
                        + "    android:layout_height=\"wrap_content\"\n"
                        + "    android:text=\"Hello World!\"\n"
                        + "    app:layout_constraintBottom_toBottomOf=\"parent\"\n"
                        + "    app:layout_constraintLeft_toLeftOf=\"parent\"\n"
                        + "    app:layout_constraintRight_toRightOf=\"parent\"\n"
                        + "    app:layout_constraintTop_toTopOf=\"parent\" />\n"
                        + "</android.support.constraint.ConstraintLayout>",
                FileUtils.join(
                        project.getSubproject("library").getMainSrcDir().getParentFile(),
                        "res",
                        "layout",
                        "liblayout.xml"),
                Charsets.UTF_8);
    }

    @Test
    public void checkResourcesLinking() throws Exception {
        project.execute("clean", ":app:assembleDebugAndroidTest");

        assertThat(project.getSubproject("app").getTestApk())
                .containsResource("layout/liblayout.xml");
    }
}
