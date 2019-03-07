/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;


import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.utils.FileUtils;
import java.util.Scanner;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the sourceSets task.
 */
public class SourceSetsTaskTest {
    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @Test
    public void runsSuccessfully() throws Exception {
        project.execute("sourceSets");

        String expected =
                "debug\n"
                        + "-----\n"
                        + "Compile configuration: debugCompile\n"
                        + "build.gradle name: android.sourceSets.debug\n"
                        + "Java sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/java")
                        + "]\n"
                        + "Manifest file: "
                        + FileUtils.toSystemDependentPath("src/debug/AndroidManifest.xml")
                        + "\n"
                        + "Android resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/res")
                        + "]\n"
                        + "Assets: ["
                        + FileUtils.toSystemDependentPath("src/debug/assets")
                        + "]\n"
                        + "AIDL sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/aidl")
                        + "]\n"
                        + "RenderScript sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/rs")
                        + "]\n"
                        + "JNI sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/jni")
                        + "]\n"
                        + "JNI libraries: ["
                        + FileUtils.toSystemDependentPath("src/debug/jniLibs")
                        + "]\n"
                        + "Java-style resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/resources")
                        + "]\n\n";

        try (Scanner scanner = project.getBuildResult().getStdout()) {
            ScannerSubject.assertThat(scanner).contains(expected);
        }
    }
}
