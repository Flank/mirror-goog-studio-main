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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.ZipFileSubject
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
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
        val appDir = project.projectDir
        createOriginalSoFile(appDir, "x86", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "arm64-v8a", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "armeabi-v7a", "libapp.so", "app:abcd")
        createOriginalSoFile(appDir, "x86_64", "libapp.so", "app:abcd")
    }

    @Test
    fun testInjectedAbiChange_WithSplits() {
        enableSplits(listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a"))
        // Run the first build with a target ABI, check that only the APK for that ABI is generated
        // and that APK only contains native libraries for target ABI
        var result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        val apk = project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG)
        assertThat(apk) {
            it.contains("lib/" + Abi.X86.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.ARM64_V8A.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.X86_64.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.ARMEABI_V7A.tag + "/libapp.so")
        }

        // Run the second build with another target ABI, check that another APK for that ABI is
        // generated (and generated correctly--regression test for
        // https://issuetracker.google.com/issues/38481325)
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        val armeabiV7aLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG).toPath()
        )

        // Run the third build without any target ABI, check that the APKs for all ABIs are
        // generated (or regenerated)
        result = project.executor().run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        PathSubject.assertThat(
            project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG).toPath()
        ).isNewerThan(armeabiV7aLastModifiedTime)

        val x86LastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG).toPath()
        )

        // Run the fourth build with a target ABI, check that the APK for that ABI is re-generated
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertCorrectApk(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("arm64-v8a", GradleTestProject.ApkType.DEBUG))

        PathSubject.assertThat(
            project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG).toPath()
        ).isNewerThan(x86LastModifiedTime)
    }

    @Test
    fun testInjectedAbiChange_WithoutSplits() {
        // Run the first build with a target ABI, check that no split APKs are generated
        // and main APK only contains native libraries for target ABI
        var result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertCorrectApk(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))

        val apk = project.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        assertThat(apk) {
            it.contains("lib/" + Abi.X86.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.ARM64_V8A.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.X86_64.tag + "/libapp.so")
            it.doesNotContain("lib/" + Abi.ARMEABI_V7A.tag + "/libapp.so")
        }

        val apkLastModifiedTime = java.nio.file.Files.getLastModifiedTime(
            project.getApkAsFile(GradleTestProject.ApkType.DEBUG).toPath()
        )

        // Run the second build with another target ABI, again check that no split APKs are
        // generated (and the main APK is re-generated)
        result = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
            .run("assembleDebug")
        assertThat(result.getTask(":packageDebug")).didWork()

        assertCorrectApk(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("armeabi-v7a", GradleTestProject.ApkType.DEBUG))

        PathSubject.assertThat(project.getApkAsFile(GradleTestProject.ApkType.DEBUG).toPath())
            .isNewerThan(apkLastModifiedTime)
    }

    @Test
    fun testMissingSoFiles_WithoutSplits() {
        // Build first with all .so files present. Inject x86_64 first, followed by x86
        val result1 = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64,x86")
            .run("assembleDebug")

        // we expect x86_64 .so files in the APK (and no x86 .so files) since there are x86_64 .so
        // files available, and we also don't expect a warning about missing .so files in this case.
        val apk1 = project.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        assertCorrectApk(apk1) {
            it.contains("lib/x86_64/libapp.so")
            it.doesNotContain("lib/x86/libapp.so")
        }
        result1.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).doesNotContain("There are no .so files available")
        }

        // remove x86_64 .so files
        removeSoFiles(listOf("x86_64"))
        val jniLibsDir = FileUtils.join(project.projectDir, "src", "main", "jniLibs")
        PathSubject.assertThat(jniLibsDir).exists()
        assertThat(jniLibsDir.listFiles()?.toList()?.map { it.name }).doesNotContain("x86_64")

        // Build again with the same command.
        val result2 = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64,x86")
            .run("assembleDebug")

        // we expect no .so files in the APK since there are no x86_64 .so files available, and we
        // also expect a warning about the missing .so files.
        val apk2 = project.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        assertCorrectApk(apk2) {
            it.doesNotContain("lib/x86_64/libapp.so")
            it.doesNotContain("lib/x86/libapp.so")
        }
        result2.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(
                "There are no .so files available to package in the APK for x86_64."
            )
        }

        // Add explicit abiFilters and remove x86 .so files
        TestFileUtils.appendToFile(
            project.buildFile,
            "android.defaultConfig.ndk.abiFilters 'x86_64', 'x86', 'arm64-v8a'"
        )
        removeSoFiles(listOf("x86"))
        PathSubject.assertThat(jniLibsDir).exists()
        assertThat(jniLibsDir.listFiles()?.toList()?.map { it.name }).doesNotContain("x86")

        // Build again with the same command.
        val result3 = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64,x86")
            .run("assembleDebug")

        // we expect only arm64-v8a .so files in the APK, and we also expect a warning about the
        // missing x86 and x86_64 .so files.
        val apk3 = project.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        assertCorrectApk(apk3) {
            it.contains("lib/arm64-v8a/libapp.so")
            it.doesNotContain("lib/x86_64/libapp.so")
            it.doesNotContain("lib/x86/libapp.so")
        }
        result3.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(
                "There are no .so files available to package in the APK for x86, x86_64."
            )
        }
    }

    @Test
    fun testMissingSoFiles_WithSplits() {
        enableSplits(listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a"))

        // Build first with all .so files present. Inject x86_64 first, followed by x86
        val result1 = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64,x86")
            .run("assembleDebug")

        // we expect the x86_64 APK to be created with the x86_64 .so files, and we also don't
        // expect a warning about missing .so files in this case.
        val apk1 = project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG)
        assertCorrectApk(apk1) {
            it.contains("lib/x86_64/libapp.so")
            it.doesNotContain("lib/x86/libapp.so")
        }
        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        result1.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).doesNotContain("There are no .so files available")
        }

        // remove x86_64 .so files
        removeSoFiles(listOf("x86_64"))
        val jniLibsDir = FileUtils.join(project.projectDir, "src", "main", "jniLibs")
        PathSubject.assertThat(jniLibsDir).exists()
        assertThat(jniLibsDir.listFiles()?.toList()?.map { it.name }).doesNotContain("x86_64")

        // Build again with the same command.
        val result2 = project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86_64,x86")
            .run("assembleDebug")

        // we expect the x86_64 APK to be created, but we don't expect any .so files in the APK
        // since there were no "source" x86_64 .so files. We expect a warning about the missing .so
        // files.
        val apk2 = project.getApkAsFile("x86_64", GradleTestProject.ApkType.DEBUG)
        assertCorrectApk(apk2) {
            it.doesNotContain("lib/x86_64/libapp.so")
            it.doesNotContain("lib/x86/libapp.so")
        }
        assertDoesNotExist(project.getApkAsFile(GradleTestProject.ApkType.DEBUG))
        assertDoesNotExist(project.getApkAsFile("x86", GradleTestProject.ApkType.DEBUG))
        result2.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(
                "There are no .so files available to package in the APK for x86_64."
            )
        }
    }

    @Test
    fun testPackagingTargetAbiCanBeDisabled() {
        // Run the build with target ABI but set BUILD_ONLY_TARGET_ABI to false,
        // check that APK contains native libraries for multiple ABIs
        project.executor()
            .with(StringOption.IDE_BUILD_TARGET_ABI, "x86")
            .with(BooleanOption.BUILD_ONLY_TARGET_ABI, false)
            .run("clean", "assembleDebug")

        val apk = project.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        assertThat(apk) {
            it.contains("lib/" + Abi.X86.tag + "/libapp.so")
            it.contains("lib/" + Abi.ARM64_V8A.tag + "/libapp.so")
        }
    }

    private fun assertCorrectApk(apk: File, ops: (ZipFileSubject) -> Unit) {
        assertThat(apk) {
            it.exists()
            it.contains("META-INF/MANIFEST.MF")
            it.contains("res/layout/main.xml")
            it.contains("AndroidManifest.xml")
            it.contains("classes.dex")
            it.contains("resources.arsc")
            ops(it)
        }
    }

    private fun assertCorrectApk(apk: File) {
        assertCorrectApk(apk) {}
    }

    private fun assertDoesNotExist(apk: File) {
        PathSubject.assertThat(apk).doesNotExist()
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

    private fun removeSoFiles(abis: List<String>) {
        abis.forEach {
            FileUtils.deleteRecursivelyIfExists(
                FileUtils.join(project.projectDir, "src", "main", "jniLibs", it)
            )
        }
    }

    private fun enableSplits(abis: List<String>) {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    splits {
                        abi {
                            enable true
                            reset()
                            include ${abis.joinToString { "'$it'" }}
                            universalApk false
                        }
                    }
                }""".trimIndent()
        )
    }
}
