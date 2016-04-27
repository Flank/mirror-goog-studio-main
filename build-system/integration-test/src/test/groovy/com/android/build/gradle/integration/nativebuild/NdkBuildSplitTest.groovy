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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import groovy.transform.CompileStatic
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
/**
 * Assemble tests for ndk-build splits.
 */
@CompileStatic
class NdkBuildSplitTest {
    @ClassRule
    static public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder()
                .withJniDir("cxx")
                .build())
            .addFile(HelloWorldJniApp.androidMkC("src/main/cxx"))
            .create();

    @BeforeClass
    static void setUp() {
        project.buildFile <<
"""
apply plugin: 'com.android.application'
import com.android.build.OutputFile;
ext.versionCodes = ["armeabi-v7a":1, "mips":2, "x86":3, "mips64":4, "all":0]
android {
    compileSdkVersion rootProject.latestCompileSdk
    buildToolsVersion = rootProject.buildToolsVersion
    generatePureSplits true

    // This actual the app version code. Giving ourselves 100,000 values [0, 99999]
    defaultConfig.versionCode = 123

    defaultConfig {
        externalNativeBuild {
          ndkBuild {
            path file("src/main/cxx/Android.mk")
            cFlags = "-DTEST_FLAG"
          }
        }
    }

    productFlavors {
        gingerbread {
            minSdkVersion 10
            versionCode = 1
        }
        icecreamSandwich {
            minSdkVersion 14
            versionCode = 2
        }
        current {
            minSdkVersion rootProject.latestCompileSdk
            versionCode = 3
        }
    }

    splits {
        abi {
            enable = true
            universalApk = true
            exclude "x86_64", "arm64-v8a", "armeabi"
        }
    }

    // make per-variant version code
    applicationVariants.all { variant ->
        // get the version code for the flavor
        def apiVersion = variant.productFlavors.get(0).versionCode

        // assign a composite version code for each output, based on the flavor above
        // and the density component.
        variant.outputs.each { output ->
            // get the key for the abi component
            def key = output.getFilter(OutputFile.ABI) == null ? "all" : output.getFilter(OutputFile.ABI)

            // set the versionCode on the output.
            output.versionCodeOverride = apiVersion * 1000000 + project.ext.versionCodes.get(key) * 100000 + defaultConfig.versionCode
        }
    }
}
"""
        project.execute("clean", "assembleDebug")
    }

    @Test
    void "check apk content"() {
        assertThatApk(project.getApk("current", "debug_armeabi-v7a")).hasVersionCode(3000123);
        assertThatApk(project.getApk("current", "debug_armeabi-v7a")).contains("lib/armeabi-v7a/libhello-jni.so");
        assertThatApk(project.getApk("current", "debug_mips64")).hasVersionCode(3000123);
        assertThatApk(project.getApk("current", "debug_mips64")).contains("lib/mips64/libhello-jni.so");
    }
}
