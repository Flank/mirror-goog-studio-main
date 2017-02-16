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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.testutils.apk.Apk
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import groovy.transform.CompileStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for CMake.
 */
@CompileStatic
class CmakeJniLibTest {

    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("ndkJniLib")
            .addFile(HelloWorldJniApp.cmakeLists("lib"))
            .create()

    @BeforeClass
    static void setUp() {
        new File(project.getTestDir(), "src/main/jni")
            .renameTo(new File(project.getTestDir(), "src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib")
        lib.buildFile <<
"""
apply plugin: 'com.android.library'
android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion
}
"""
        project.execute("clean", "assembleDebug",
                "generateJsonModelDebug", "generateJsonModelRelease")
    }

    @AfterClass
    static void cleanUp() {
        project = null
    }

    @Test
    void "check apk content"() {
        GradleTestProject app = project.getSubproject("app");
        Apk gingerbreadUniversal = app.getApk("universal",  GradleTestProject.ApkType.DEBUG, "gingerbread");
        if (!gingerbreadUniversal.exists()) {
            throw new RuntimeException(String.format("Could not find %s", gingerbreadUniversal));
        }
        assertThatApk(app.getApk("universal",  GradleTestProject.ApkType.DEBUG, "gingerbread"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("armeabi-v7a",  GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(app.getApk("x86",  GradleTestProject.ApkType.DEBUG, "icecreamSandwich"))
                .doesNotContain("lib/armeabi-v7a/libhello-jni.so");
    }

    @Test
    public void checkModel() {
        // Make sure we can successfully get AndroidProject
        project.model().getMulti().getModelMap().get(":app");

        NativeAndroidProject model = project.model()
                .getMulti(NativeAndroidProject.class).get(":lib");
        assertThat(model).isNotNull();
        assertThat(model.buildFiles).hasSize(1);
        assertThat(model.name).isEqualTo("lib");
        assertThat(model.artifacts).hasSize(14);
        assertThat(model.fileExtensions).hasSize(1);

        for (File file : model.buildFiles) {
            assertThat(file).isFile();
        }

        Multimap<String, NativeArtifact> groupToArtifacts = ArrayListMultimap.create();

        for (NativeArtifact artifact : model.artifacts) {
            List<String> pathElements = TestFileUtils.splitPath(artifact.getOutputFile());
            assertThat(pathElements).contains("obj");
            groupToArtifacts.put(artifact.getGroupName(), artifact);
        }

        assertThat(groupToArtifacts.keySet()).containsExactly("debug", "release");
        assertThat(groupToArtifacts.get("debug")).hasSize(groupToArtifacts.get("release").size());
    }

    @Test
    @Category(DeviceTests.class)
    void connectedCheck() {
        project.executeConnectedCheck()
    }
}
