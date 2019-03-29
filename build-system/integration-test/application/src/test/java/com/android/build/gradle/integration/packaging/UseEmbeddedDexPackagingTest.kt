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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class UseEmbeddedDexPackagingTest {
    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.test")
                .withFile("build.gradle",
                    """
                        apply plugin: 'com.android.application'
                        android {
                          compileSdkVersion 'android-Q'
                        }
                    """.trimIndent())
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                         xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="com.test">
                    <application android:useEmbeddedDex="true"/>
                </manifest>
                    """.trimIndent()
                )
        )
        .create()

    @Test
    fun testDexIsUncompressed() {
        project.executor().run("assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        ZipFile(apk.file.toFile()).use {
            val classesDex = it.getEntry("classes.dex")
            assertThat(classesDex.method).isEqualTo(ZipEntry.STORED)
        }
    }
}