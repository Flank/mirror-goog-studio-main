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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.apk.Apk
import com.android.testutils.truth.MoreTruth.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test APK is packaged correctly when injected ABI exists or changes */
class InjectedAbiTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setup() {
        val appDir = project.testDir
        createOriginalSoFile(appDir, "x86", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "arm64-v8a", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "armeabi-v7a", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "x86_64", "libapp.so", "app:abcd")
    }

    @Test
    fun testAbiChangeWithSplits() {
        TestFileUtils.appendToFile(
            project.buildFile, """
                android {
                    splits {
                        abi {
                            enable true
                            reset()
                            include 'x86', 'armeabi-v7a', 'x86_64', 'arm64-v8a'
                            universalApk false
                        }
                    }
                }
            """.trimIndent()
        )
        // Run the first build with a target ABI, check that only the APK for that ABI is generated
        // and that APK only contains native libraries for target ABI
        var result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG))
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG)).doesNotExist()

        val apk = project.getApk("x86", GradleTestProject.ApkType.DEBUG)
        assertThat(apk).contains("lib/" + Abi.X86.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.ARM64_V8A.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.X86_64.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.ARMEABI_V7A.getName() + "/libapp.so")

        var x86LastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("x86", GradleTestProject.ApkType.DEBUG).file
        )

        // Run the second build with another target ABI, check that another APK for that ABI is
        // generated (and generated correctly--regression test for
        // https://issuetracker.google.com/issues/38481325)
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG)).doesNotExist()

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).file)
            .wasModifiedAt(x86LastModifiedTime)
        var armeabiV7aLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).file
        )

        // Run the third build without any target ABI, check that the APKs for all ABIs are
        // generated (or regenerated)
        result = project.executor().run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).file)
            .isNewerThan(x86LastModifiedTime)
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).file)
            .isNewerThan(armeabiV7aLastModifiedTime)

        x86LastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("x86", GradleTestProject.ApkType.DEBUG).file
        )
        armeabiV7aLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).file
        )
        val x8664LastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("x86_64", GradleTestProject.ApkType.DEBUG).file
        )
        val armeabiV8aLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG).file
        )

        // Run the fourth build with a target ABI, check that the APK for that ABI is re-generated
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertCorrectApk(project.getApk("x86", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG).file)
            .isNewerThan(x86LastModifiedTime)
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG).file)
            .wasModifiedAt(armeabiV7aLastModifiedTime)
        assertThat(project.getApk("x86_64", GradleTestProject.ApkType.DEBUG).file)
            .wasModifiedAt(x8664LastModifiedTime)
        assertThat(project.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG).file)
            .wasModifiedAt(armeabiV8aLastModifiedTime)
    }

    @Test
    fun testAbiChangeWithoutSplits() {
        // Run the first build with a target ABI, check that no split APKs are generated
        // and main APK only contains native libraries for target ABI
        var result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertCorrectApk(project.getApk(GradleTestProject.ApkType.DEBUG))
        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist()

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).contains("lib/" + Abi.X86.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.ARM64_V8A.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.X86_64.getName() + "/libapp.so")
        assertThat(apk).doesNotContain("lib/" + Abi.ARMEABI_V7A.getName() + "/libapp.so")

        val apkLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApk(GradleTestProject.ApkType.DEBUG).file
        )

        // Run the second build with another target ABI, again check that no split APKs are
        // generated (and the main APK is re-generated)
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertCorrectApk(project.getApk(GradleTestProject.ApkType.DEBUG))
        assertThat(project.getApk("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist()
        assertThat(project.getApk("armeabi-v7a", GradleTestProject.ApkType.DEBUG)).doesNotExist()

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG).file)
            .isNewerThan(apkLastModifiedTime)
    }

    @Test
    fun testPackagingTargetAbiCanBeDisabled() {
        // Run the build with target ABI but set BUILD_ONLY_TARGET_ABI to false,
        // check that APK contains native libraries for multiple ABIs
        project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .with(BooleanOption.BUILD_ONLY_TARGET_ABI, false)
            .run("clean", "assembleDebug")

        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).contains("lib/" + Abi.X86.getName() + "/libapp.so")
        assertThat(apk).contains("lib/" + Abi.ARM64_V8A.getName() + "/libapp.so")
    }

    private fun assertCorrectApk(apk: Apk) {
        assertThat(apk).exists()
        assertThat(apk).contains("META-INF/MANIFEST.MF")
        assertThat(apk).contains("res/layout/main.xml")
        assertThat(apk).contains("AndroidManifest.xml")
        assertThat(apk).contains("classes.dex")
        assertThat(apk).contains("resources.arsc")
    }

    private fun createOriginalSoFile(
        projectFolder: File,
        abi: String,
        filename: String,
        content: String
    ) {
        val assetFolder = FileUtils.join(projectFolder, "src", "main", "jniLibs", abi)
        FileUtils.mkdirs(assetFolder)
        Files.asCharSink(File(assetFolder, filename), Charsets.UTF_8).write(content)
    }
}
