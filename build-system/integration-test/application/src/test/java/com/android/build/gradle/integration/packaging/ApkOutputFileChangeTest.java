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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.StringOption;
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
    public void testAbiChangeWithSplits() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a', 'x86_64', 'arm64-v8a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // Run the first build with a target ABI, check that only the APK for that ABI is generated
        GradleBuildResult result =
                project.executor()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                        .run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG));
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        long x86LastModifiedTime =
                project.getApk("x86", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();

        // Run the second build with another target ABI, check that another APK for that ABI is
        // generated (and generated correctly--regression test for
        // https://issuetracker.google.com/issues/38481325)
        result =
                project.executor()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                        .run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG));
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).getFile())
                .wasModifiedAt(x86LastModifiedTime);
        long armeabiV7aLastModifiedTime =
                project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();

        // Run the third build without any target ABI, check that the APKs for all ABIs are
        // generated (or regenerated)
        result = project.executor().run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG));

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).getFile())
                .isNewerThan(x86LastModifiedTime);
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).getFile())
                .isNewerThan(armeabiV7aLastModifiedTime);

        x86LastModifiedTime =
                project.getApk("x86", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();
        armeabiV7aLastModifiedTime =
                project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();
        long x8664LastModifiedTime =
                project.getApk("x86_64", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();
        long armeabiV8aLastModifiedTime =
                project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG)
                        .getFile()
                        .toFile()
                        .lastModified();

        // Run the fourth build with a target ABI, check that the APK for that ABI is re-generated
        result =
                project.executor()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                        .run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG));
        assertCorrectApk(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG));

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).getFile())
                .isNewerThan(x86LastModifiedTime);
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).getFile())
                .wasModifiedAt(armeabiV7aLastModifiedTime);
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG).getFile())
                .wasModifiedAt(x8664LastModifiedTime);
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG).getFile())
                .wasModifiedAt(armeabiV8aLastModifiedTime);
    }

    @Test
    public void testAbiChangeWithoutSplits() throws Exception {
        // Run the first build with a target ABI, check that no split APKs are generated
        GradleBuildResult result =
                project.executor()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
                        .run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();

        assertCorrectApk(project.getApk(GradleTestProject.ApkType.DEBUG));
        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        long apkLastModifiedTime =
                project.getApk(GradleTestProject.ApkType.DEBUG).getFile().toFile().lastModified();

        // Run the second build with another target ABI, again check that no split APKs are
        // generated (and the main APK is not re-generated)
        result =
                project.executor()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                        .run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasUpToDate();

        assertCorrectApk(project.getApk(GradleTestProject.ApkType.DEBUG));
        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).getFile())
                .wasModifiedAt(apkLastModifiedTime);
    }

    @Test
    public void testOutputFileNameChange() throws Exception {
        // Run the first build
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();
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
        assertThat(result.getTask(":packageDebug")).wasNotUpToDate();
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
