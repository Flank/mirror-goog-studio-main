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
import com.android.SdkConstants.FORCE_COMPILE_SDK_PREVIEW_PROPERTY
import com.android.SdkConstants.MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_EXTENSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.compileSdkHash
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask
import com.android.builder.core.ToolsRevisionUtils
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.android.zipflinger.BytesSource
import com.android.zipflinger.ZipArchive
import com.google.common.base.Throwables
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
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
                .isEqualTo(
                    """
                        An issue was found when checking AAR metadata:

                          1.  Dependency ':lib' requires libraries and applications that
                              depend on it to compile against version 28 or later of the
                              Android APIs.

                              :app is currently compiled against android-24.

                              Recommended action: Update this project to use a newer compileSdkVersion
                              of at least 28, for example ${ToolsRevisionUtils.MAX_RECOMMENDED_COMPILE_SDK_VERSION.apiLevel}.

                              Note that updating a library or application's compileSdkVersion (which
                              allows newer APIs to be used) can be done separately from updating
                              targetSdkVersion (which opts the app in to new runtime behavior) and
                              minSdkVersion (which determines which devices the app can be installed
                              on).
                """.trimIndent())

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
                .isEqualTo(
                    """
                    An issue was found when checking AAR metadata:

                      1.  Dependency 'library.aar' requires libraries and applications that
                          depend on it to compile against version 28 or later of the
                          Android APIs.

                          :app is currently compiled against android-24.

                          Recommended action: Update this project to use a newer compileSdkVersion
                          of at least 28, for example ${ToolsRevisionUtils.MAX_RECOMMENDED_COMPILE_SDK_VERSION.apiLevel}.

                          Note that updating a library or application's compileSdkVersion (which
                          allows newer APIs to be used) can be done separately from updating
                          targetSdkVersion (which opts the app in to new runtime behavior) and
                          minSdkVersion (which determines which devices the app can be installed
                          on).
                """.trimIndent())
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
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  The AAR metadata for dependency 'library.aar' does not specify an
                              aarFormatVersion value, which is a required value.
                    """.trimIndent()
                )
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
            assertThat(Throwables.getRootCause(e).message).startsWith("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'library.aar' has an aarFormatVersion value of
                      '99999', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.
            """.trimIndent())
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
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  The AAR metadata for dependency 'library.aar' has an invalid
                              aarFormatVersion value (invalid).

                              Invalid revision: invalid
                    """.trimIndent()
                )
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
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  The AAR metadata for dependency 'library.aar' does not specify an
                              aarMetadataVersion value, which is a required value.
                    """.trimIndent()
                )
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
            assertThat(Throwables.getRootCause(e).message).startsWith("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'library.aar' has an aarMetadataVersion value of
                      '99999', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.
                """.trimIndent())
        }
    }

    @Test
    fun testIncompatibleAgpVersion() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minAgpVersion = "99999.0.0"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .contains("requires Android Gradle plugin 99999.0.0 or higher.")
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
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  The AAR metadata for dependency 'library.aar' has an invalid
                              aarMetadataVersion value (invalid).

                              Invalid revision: invalid
                    """.trimIndent()
                )
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
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  The AAR metadata for dependency 'library.aar' has an invalid
                              minCompileSdk value (invalid).

                              minCompileSdk must be an integer.
                    """.trimIndent()
                )
        }
    }

    @Test
    fun testMultipleInvalidValues() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = "invalid",
            aarMetadataVersion = "invalid",
            minCompileSdk = "invalid",
            minAgpVersion = "invalid",
            minCompileSdkExtension = "invalid"
        )
        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:assembleDebug")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message).contains(AAR_FORMAT_VERSION_PROPERTY)
            assertThat(Throwables.getRootCause(e).message).contains(AAR_METADATA_VERSION_PROPERTY)
            assertThat(Throwables.getRootCause(e).message).contains(MIN_COMPILE_SDK_PROPERTY)
            assertThat(Throwables.getRootCause(e).message)
                .contains(MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY)
            assertThat(Throwables.getRootCause(e).message)
                .contains(MIN_COMPILE_SDK_EXTENSION_PROPERTY)
        }
    }

    @Test
    fun testPassingWithForceCompileSdkPreview() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            forceCompileSdkPreview = "TiramisuPrivacySandbox"
        )
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            "\n\nandroid.compileSdkPreview 'TiramisuPrivacySandbox'\n\n"
        )
        val result = project.executor().run(":app:checkDebugAarMetadata")
        ScannerSubject.assertThat(result.stdout).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun testFailsWithForceCompileSdkPreview() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            forceCompileSdkPreview = "TiramisuPrivacySandbox"
        )

        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:checkDebugAarMetadata")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  Dependency 'library.aar' requires libraries and applications that
                              depend on it to compile against codename "TiramisuPrivacySandbox" of the
                              Android APIs.

                              :app is currently compiled against $compileSdkHash.

                              Recommended action: Use a different version of dependency 'library.aar',
                              or set compileSdkPreview to "TiramisuPrivacySandbox" in your build.gradle
                              file if you intend to experiment with that preview SDK.
                    """.trimIndent()
                )
        }
    }

    @Test
    fun testPassingWithMinCompileSdkExtension() {
        // Test with minCompileSdkExtension = "1" to test that CheckAarMetadataTask can actually
        //  parse the platform extension level correctly. This means that we have to inject a
        //  non-zero extension level because all current SDKs omit the extension level. android-33
        //  should have a non-zero extension level, so we can stop injecting the extension level
        //  once android-33 is released.
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdkExtension = "1"
        )
        val sdkLocation = project.androidSdkDir
        val progress = FakeProgressIndicator()
        val sdkHandler =
            AndroidSdkHandler.getInstance(AndroidLocationsSingleton, sdkLocation!!.toPath())
        val targetManager = sdkHandler.getAndroidTargetManager(progress)
        val target = targetManager.getTargetFromHashString(compileSdkHash, progress)
        val targetLocation = File(target.location)
        val packageXml = targetLocation.resolve("package.xml")
        PathSubject.assertThat(packageXml).exists()
        if (!packageXml.readText().contains("<extension-level>")) {
            TestFileUtils.searchAndReplace(
                packageXml,
                "</codename>",
                "</codename><extension-level>1</extension-level>"
            )
        }

        val result = project.executor().run(":app:checkDebugAarMetadata")
        ScannerSubject.assertThat(result.stdout).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun testFailsWithMinCompileSdkExtension() {
        addAarWithPossiblyInvalidAarMetadataToAppProject(
            aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
            aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
            minCompileSdkExtension = "1000"
        )

        // Test that build fails with desired error message.
        try {
            project.executor().run(":app:checkDebugAarMetadata")
            Assert.fail("Expected build failure")
        } catch (e: Exception) {
            assertThat(Throwables.getRootCause(e).message)
                .startsWith(
                    """
                        An issue was found when checking AAR metadata:

                          1.  Dependency 'library.aar' requires libraries and applications that
                              depend on it to compile against an SDK with an extension level of
                              1000 or higher.

                              Recommended action: Update this project to use a compileSdkExtension
                              value of at least 1000.
                    """.trimIndent()
                )
        }
    }

    private fun addAarWithPossiblyInvalidAarMetadataToAppProject(
        aarFormatVersion: String?,
        aarMetadataVersion: String?,
        minCompileSdk: String? = null,
        minAgpVersion: String? = null,
        forceCompileSdkPreview: String? = null,
        minCompileSdkExtension: String? = null
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
            minCompileSdkExtension?.let { sb.appendln("$MIN_COMPILE_SDK_EXTENSION_PROPERTY=$it") }
            minAgpVersion?.let { sb.appendln("$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=$it") }
            forceCompileSdkPreview?.let { sb.appendln("$FORCE_COMPILE_SDK_PREVIEW_PROPERTY=$it") }
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
