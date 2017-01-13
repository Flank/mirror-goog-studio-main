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

package com.android.build.gradle.integration.application

import com.android.annotations.NonNull
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.Apk
import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk

/**
 * Test for the jarjar integration.
 */
@CompileStatic
@RunWith(FilterableParameterized)
public class JarJarTest {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<String> projects() {
        return ImmutableList.of("jarjarWithJack", "jarjarIntegration");
    }

    @Rule
    public GradleTestProject project;

    public JarJarTest(@NonNull String projectName) {
        project = GradleTestProject.builder().fromTestProject(projectName).create();
    }

    @Test
    void "check repackaged gson library for monodex"() {
        project.executeAndReturnModel("clean", "assembleDebug")
        verifyApk()
    }

    @Test
    void "check repackaged for native multidex"() {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" +
                        "android.defaultConfig {\n" +
                        "    minSdkVersion 21\n" +
                        "    multiDexEnabled true\n" +
                        "}\n");

        project.executeAndReturnModel("clean", "assembleDebug")
        verifyApk()
    }

    @Test
    void "check repackaged for legacy multidex"() {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" +
                        "android.defaultConfig {\n" +
                        "    minSdkVersion 19\n" +
                        "    multiDexEnabled true\n" +
                        "}\n");

        project.executeAndReturnModel("clean", "assembleDebug")
        verifyApk()
    }

    private void verifyApk() {
        // make sure the Gson library has been renamed and the original one is not present.
        Apk outputFile = project.getApk("debug");
        assertThatApk(outputFile).containsClass("Lcom/google/repacked/gson/Gson;");
        assertThatApk(outputFile).doesNotContainClass("Lcom/google/gson/Gson;")
    }
}