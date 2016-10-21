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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.utils.FileUtils;
import com.google.api.client.util.StringUtils;
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
                "debug"
                        + StringUtils.LINE_SEPARATOR
                        + "-----"
                        + StringUtils.LINE_SEPARATOR
                        + "Compile configuration: debugCompile"
                        + StringUtils.LINE_SEPARATOR
                        + "build.gradle name: android.sourceSets.debug"
                        + StringUtils.LINE_SEPARATOR
                        + "Java sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/java")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "Manifest file: "
                        + FileUtils.toSystemDependentPath("src/debug/AndroidManifest.xml")
                        + StringUtils.LINE_SEPARATOR
                        + "Android resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/res")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "Assets: ["
                        + FileUtils.toSystemDependentPath("src/debug/assets")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "AIDL sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/aidl")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "RenderScript sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/rs")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "JNI sources: ["
                        + FileUtils.toSystemDependentPath("src/debug/jni")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "JNI libraries: ["
                        + FileUtils.toSystemDependentPath("src/debug/jniLibs")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + "Java-style resources: ["
                        + FileUtils.toSystemDependentPath("src/debug/resources")
                        + "]"
                        + StringUtils.LINE_SEPARATOR
                        + ""
                        + StringUtils.LINE_SEPARATOR;

        assertThat(project.getStdout()).contains(expected);
    }
}
