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
package com.android.build.gradle.integration.application

import com.android.SdkConstants.AAR_FORMAT_VERSION_PROPERTY
import com.android.SdkConstants.AAR_METADATA_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask
import com.android.utils.FileUtils
import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import com.google.common.base.Throwables
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.StringBuilder
import java.nio.file.Files
import java.util.zip.Deflater

/** Tests for [CheckAarMetadataTask]. */
class CheckAarMetadataTaskTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testBasic() {
        // Test that app builds successfully when compatible minCompileSdkVersion set library.
        project.getSubproject("lib").buildFile.appendText(
            "android.defaultConfig.aarMetadata.minCompileSdk 28"
        )
        project.executor().run(":app:assembleDebug")
    }

    @Test
    fun testMinCompileSdkVersion_librarySubModule() {
        // Add resource requiring API level 28 to library
        FileUtils.join(
            project.getSubproject("lib").projectDir,
            "src",
            "main",
            "res",
            "values",
            "values.xml"
        ).also {
            it.parentFile.mkdirs()
        }.writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <style name="Foo">
                            <item name="dialogCornerRadius">?android:attr/dialogCornerRadius</item>
                        </style>
                    </resources>
                """.trimIndent()
        )

        // Set app's compileSdkVersion to 24.
        project.getSubproject("app").buildFile
        TestFileUtils.searchRegexAndReplace(
            project.getSubproject("app").buildFile,
            "compileSdkVersion \\d+",
            "compileSdkVersion 24"
        )

        // First test that the build fails when minCompileSdkVersion isn't set.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("Android resource linking failed")
        }

        // Then test that setting minCompileSdkVersion results in a better error message.
        project.getSubproject("lib").buildFile.appendText(
            "android.defaultConfig.aarMetadata.minCompileSdk 28"
        )
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("greater than this module's compileSdkVersion (android-24)")
        }
    }

    @Test
    fun testMinCompileSdkVersion_aarFileDependency() {
        // Add resource requiring API level 28 to library
        FileUtils.join(
            project.getSubproject("lib").projectDir,
            "src",
            "main",
            "res",
            "values",
            "values.xml"
        ).also {
            it.parentFile.mkdirs()
        }.writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <style name="Foo">
                            <item name="dialogCornerRadius">?android:attr/dialogCornerRadius</item>
                        </style>
                    </resources>
                """.trimIndent()
        )
        project.getSubproject("lib").buildFile.appendText(
            "android.defaultConfig.aarMetadata.minCompileSdk 28"
        )
        project.executor().run(":lib:assembleDebug")
        // Copy lib's .aar build output to the app's libs directory
        FileUtils.copyFile(
            project.getSubproject("lib").getOutputFile("aar", "lib-debug.aar"),
            File(
                File(project.getSubproject("app").projectDir, "libs").also { it.mkdirs() },
                "library.aar"
            )
        )

        // Set app's compileSdkVersion to 24.
        project.getSubproject("app").buildFile
        TestFileUtils.searchRegexAndReplace(
            project.getSubproject("app").buildFile,
            "compileSdkVersion \\d+",
            "compileSdkVersion 24"
        )

        // Replace app's dependency on the library module with a dependency on the AAR file
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "project(':lib')",
            "files('libs/library.aar')"
        )

        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("greater than this module's compileSdkVersion (android-24)")
        }
    }

    @Test
    fun testMissingAarFormatVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = null,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("not specify an $AAR_FORMAT_VERSION_PROPERTY value")
        }
    }

    @Test
    fun testIncompatibleAarFormatVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = "99999",
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("Please upgrade to a newer version of the Android Gradle Plugin.")
        }
    }

    @Test
    fun testInvalidAarFormatVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = "invalid",
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $AAR_FORMAT_VERSION_PROPERTY value.")
        }
    }

    @Test
    fun testMissingAarMetadataVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = null
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("not specify an $AAR_METADATA_VERSION_PROPERTY value")
        }
    }

    @Test
    fun testIncompatibleAarMetadataVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = "99999"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("Please upgrade to a newer version of the Android Gradle Plugin.")
        }
    }

    @Test
    fun testInvalidAarMetadataVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = "invalid"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $AAR_METADATA_VERSION_PROPERTY value.")
        }
    }

    @Test
    fun testInvalidMinCompileSdk() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdk = "invalid"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $MIN_COMPILE_SDK_PROPERTY value.")
        }
    }

    @Test
    fun testMultipleInvalidValues() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = "invalid",
            aarMetadataVersion = "invalid",
            minCompileSdk = "invalid"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $AAR_FORMAT_VERSION_PROPERTY value.")
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $AAR_METADATA_VERSION_PROPERTY value.")
            assertThat(Throwables.getRootCause(e).message)
                .contains("has an invalid $MIN_COMPILE_SDK_PROPERTY value.")
        }
    }

    private fun addAarWithPossiblyInvalidAarMetadataToAppProject(
        aarFormatVersion: String?,
        aarMetadataVersion: String?,
        minCompileSdk: String? = null
    ) {
        project.executor().run(":lib:assembleDebug")
        // Copy lib's .aar build output to the app's libs directory
        val aarFile = project.getSubproject("app").projectDir.toPath().resolve("libs/library.aar")
        Files.createDirectories(aarFile.parent)
        FileUtils.copyFile(
            project.getSubproject("lib").getOutputFile("aar", "lib-debug.aar"),
            aarFile.toFile()
        )

        // Manually write (possibly invalid) AAR metadata entry
        ZipArchive(aarFile).use { aar ->
            aar.delete(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            val sb = StringBuilder()
            aarFormatVersion?.let { sb.appendln("$AAR_FORMAT_VERSION_PROPERTY=$it") }
            aarMetadataVersion?.let { sb.appendln("$AAR_METADATA_VERSION_PROPERTY=$it") }
            minCompileSdk?.let { sb.appendln("$MIN_COMPILE_SDK_PROPERTY=$it") }
            aar.add(
                BytesSource(
                    sb.toString().toByteArray(),
                    AarMetadataTask.AAR_METADATA_ENTRY_PATH,
                    Deflater.NO_COMPRESSION
                )
            )
        }

        // Replace app's dependency on the library module with a dependency on the AAR file
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "project(':lib')",
            "files('libs/library.aar')"
        )
    }
}
