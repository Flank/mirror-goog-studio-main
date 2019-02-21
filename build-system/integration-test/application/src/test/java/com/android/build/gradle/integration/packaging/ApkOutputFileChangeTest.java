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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to verify that the APK is packaged correctly when there is a change in the APK output file
 * name.
 */
public class ApkOutputFileChangeTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void testOutputFileNameChange() throws Exception {
        // Run the first build
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).didWork();
        assertCorrectApk(project.getApk(GradleTestProject.ApkType.DEBUG));

        // Modify the output file name
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    android.applicationVariants.all { variant ->\n"
                        + "        variant.outputs.all {\n"
                        + "            outputFileName = 'foo.apk'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // Run the second build, check that the new APK is generated correctly (regression test for
        // https://issuetracker.google.com/issues/64703619)
        result = project.executor().run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).didWork();
        assertCorrectApk(project.getApkByFileName(GradleTestProject.ApkType.DEBUG, "foo.apk"));
    }

    private static void assertCorrectApk(@NonNull Apk apk) throws IOException {
        assertThat(apk).exists();
        assertThat(apk).contains("META-INF/MANIFEST.MF");
        assertThat(apk).contains("res/layout/main.xml");
        assertThat(apk).contains("AndroidManifest.xml");
        assertThat(apk).contains("classes.dex");
        assertThat(apk).contains("resources.arsc");
    }
}
