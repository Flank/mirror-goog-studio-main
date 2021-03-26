/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProcessManifestTest {
    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Test
    fun build() {
        project.buildFile.appendText("""
            import com.android.build.api.variant.AndroidVersion

            androidComponents {
                beforeVariants(selector().all(), { variant ->
                    variant.minSdk = 21
                    variant.maxSdk = 29
                    variant.targetSdk = 22
                })
            }
        """.trimIndent())

        project.executor().run("processDebugManifest")
        val manifestContent = File(project.buildDir, "intermediates/merged_manifest/debug/AndroidManifest.xml").readLines()
        assertManifestContent(manifestContent, "android:minSdkVersion=\"21\"")
        assertManifestContent(manifestContent, "android:targetSdkVersion=\"22\"")
        assertManifestContent(manifestContent, "android:maxSdkVersion=\"29\"")
    }

    fun assertManifestContent(manifestContent: Iterable<String>, stringToAssert: String) {
        manifestContent.forEach { if (it.trim().contains(stringToAssert)) return }
        Assert.fail("Cannot find $stringToAssert in ${manifestContent.joinToString(separator = "\n")}")
    }
}
