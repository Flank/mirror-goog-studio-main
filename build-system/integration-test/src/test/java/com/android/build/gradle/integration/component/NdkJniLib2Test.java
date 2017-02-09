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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatNativeLib;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Integration test library plugin with JNI sources.
 */
public class NdkJniLib2Test {
    private static MultiModuleTestProject testApp = new MultiModuleTestProject(ImmutableMap.of(
            ":app", new EmptyAndroidTestApp(),
            ":lib", new HelloWorldJniApp()));

    static {
        AndroidTestApp app = (AndroidTestApp) testApp.getSubproject(":app");
        app.addFile(new TestSourceFile("", "build.gradle",
                "apply plugin: \"com.android.model.application\"\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(\":lib\")\n"
                + "}\n"
                + "\n"
                + "model {\n"
                + "    android {\n"
                + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "    }\n"
                + "}\n"));

        // Create AndroidManifest.xml that uses the Activity from the library.
        app.addFile(new TestSourceFile("src/main", "AndroidManifest.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "      package=\"com.example.app\"\n"
                + "      android:versionCode=\"1\"\n"
                + "      android:versionName=\"1.0\">\n"
                + "\n"
                + "    <uses-sdk android:minSdkVersion=\"3\" />\n"
                + "    <application android:label=\"@string/app_name\">\n"
                + "        <activity\n"
                + "            android:name=\"com.example.hellojni.HelloJni\"\n"
                + "            android:label=\"@string/app_name\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>"));

        AndroidTestApp lib = (AndroidTestApp) testApp.getSubproject(":lib");
        lib.addFile(new TestSourceFile("", "build.gradle",
                "apply plugin: \"com.android.model.library\"\n"
                + "\n"
                + "model {\n"
                + "    android {\n"
                + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "        ndk {\n"
                + "            moduleName \"hello-jni\"\n"
                + "        }\n"
                + "    }\n"
                + "}\n"));
    }

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(testApp)
            .useExperimentalGradleVersion(true)
            .create();

    @AfterClass
    public static void cleanUp() {
        testApp = null;
    }

    @Test
    public void checkSoAreIncludedInBothAppAndLibrary() throws Exception {
        project.execute("clean", ":app:assembleDebug");

        Apk app = project.getSubproject("app").getApk("debug");
        assertThat(app).contains("lib/x86/libhello-jni.so");

        File lib = ZipHelper.extractFile(app, "lib/x86/libhello-jni.so");
        assertThatNativeLib(lib).isStripped();
    }

    /** Ensure prepareDependency task is executed before compilation task. */
    @Test
    public void checkTaskOrder() throws Exception {
        File emptyFile = project.getSubproject("app").file("src/main/jni/empty.c");
        FileUtils.createFile(emptyFile, "");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "model {\n"
                + "    android {\n"
                + "        ndk {\n"
                + "            moduleName \"empty\"\n"
                + "            abiFilters.add(\"armeabi\")\n"
                + "        }\n"
                + "    }\n"
                + "}\n");

        GradleBuildResult result = project.executor()
                .withArgument("--dry-run") // Just checking task order.  Don't need to actually run.
                .run(":app:assembleDebug");
        assertThat(result.getTask(":app:linkEmptyArmeabiDebugSharedLibrary"))
                .wasExecuted();
    }
}
