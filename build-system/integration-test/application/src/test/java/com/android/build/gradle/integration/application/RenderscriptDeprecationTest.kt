/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.Version
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Tests for RenderScript deprecation message. */
class RenderscriptDeprecationTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun testDeprecation() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion 14
                        targetSdkVersion 28

                        renderscriptTargetApi 28
                        renderscriptSupportModeEnabled true
                    }

                    buildFeatures {
                        renderScript true
                    }
                }
            """.trimIndent()
        )

        // Add a renderscript source file
        val renderscriptDir = FileUtils.mkdirs(project.file("src/main/rs"))
        val renderscriptFile = File(renderscriptDir, "saturation.rs")
        FileUtils.writeToFile(
            renderscriptFile,
            """

                #pragma version(1)
                #pragma rs java_package_name(com.example.android.basicrenderscript)
                #pragma rs_fp_relaxed

                const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

                float saturationValue = 0.f;

                /*
                 * RenderScript kernel that performs saturation manipulation.
                 */
                uchar4 __attribute__((kernel)) saturation(uchar4 in)
                {
                    float4 f4 = rsUnpackColor8888(in);
                    float3 result = dot(f4.rgb, gMonoMult);
                    result = mix(result, f4.rgb, saturationValue);

                    return rsPackColorTo8888(result);
                }

            """.trimIndent()
        )

        val expectedWarning =
            "The RenderScript APIs are deprecated. They will be removed in Android Gradle plugin " +
                    "${Version.VERSION_9_0.versionString}. See the following link for a guide to " +
                    "migrate from RenderScript: " +
                    "https://developer.android.com/guide/topics/renderscript/migrate"

        // We expect a deprecation warning if renderscript is enabled and there is a renderscript
        // source file.
        var result  = project.executor().run("clean", "assembleDebug")
        assertThat(result.stdout).contains(expectedWarning)

        // We expect no warning if renderscript is disabled
        TestFileUtils.appendToFile(
            project.buildFile,
            "\nandroid.buildFeatures.renderScript false\n"
        )
        result  = project.executor().run("clean", "assembleDebug")
        assertThat(result.stdout).doesNotContain(expectedWarning)

        // We expect no warning if renderscript is enabled but there are no renderscript sources
        TestFileUtils.searchAndReplace(project.buildFile, "renderScript false", "renderScript true")
        FileUtils.deleteIfExists(renderscriptFile)
        result  = project.executor().run("clean", "assembleDebug")
        assertThat(result.stdout).doesNotContain(expectedWarning)
    }
}
