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

package com.android.build.gradle.integration.common.fixture.app;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.google.common.collect.ImmutableMap;

/**
 * Simple test application with an Android library that prints "hello world!".
 */
public class HelloWorldLibraryApp extends MultiModuleTestProject implements TestProject {
    @NonNull
    public static HelloWorldLibraryApp create() {
        return new HelloWorldLibraryApp();
    }

    public HelloWorldLibraryApp() {
        super(ImmutableMap.of(":app", new EmptyAndroidTestApp(), ":lib", HelloWorldApp.noBuildFile()));

        GradleProject app = (GradleProject) getSubproject(":app");
        app.addFile(
                new TestSourceFile(
                        "build.gradle",
                        "apply plugin: 'com.android.application'\n"
                                + "\n"
                                + "dependencies {\n"
                                + "    implementation project(':lib')\n"
                                + "}\n"
                                + "\n"
                                + "android {\n"
                                + "     compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "    defaultConfig {\n"
                                + "        minSdkVersion 3\n"
                                + "    }\n"
                                + "}\n"));

        // Create AndroidManifest.xml that uses the Activity from the library.
        app.addFile(new TestSourceFile("src/main", "AndroidManifest.xml",
"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
"<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
"      package=\"com.example.app\"\n" +
"      android:versionCode=\"1\"\n" +
"      android:versionName=\"1.0\">\n" +
"\n" +
"    <application android:label=\"@string/app_name\">\n" +
"        <activity\n" +
"            android:name=\"com.example.helloworld.HelloWorld\"\n" +
"            android:label=\"@string/app_name\">\n" +
"            <intent-filter>\n" +
"                <action android:name=\"android.intent.action.MAIN\" />\n" +
"                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
"            </intent-filter>\n" +
"        </activity>\n" +
"    </application>\n" +
"</manifest>\n"));

        GradleProject lib = (GradleProject) getSubproject(":lib");
        lib.addFile(
                new TestSourceFile(
                        "build.gradle",
                        "apply plugin: 'com.android.library'\n"
                                + "\n"
                                + "android {\n"
                                + "     compileSdkVersion "
                                + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                                + "\n"
                                + "}\n"));

    }
}
