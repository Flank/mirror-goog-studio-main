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
package com.android.build.gradle.integration.library

import com.android.SdkConstants
import com.android.SdkConstants.AAR_FORMAT_VERSION_PROPERTY
import com.android.SdkConstants.AAR_METADATA_VERSION_PROPERTY
import com.android.SdkConstants.FORCE_COMPILE_SDK_PREVIEW_PROPERTY
import com.android.SdkConstants.MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_EXTENSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.apksig.internal.util.ByteBufferUtils.toByteArray
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Tests for [AarMetadataTask]. */
class AarMetadataTaskTest {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testBasic() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=1
                |$MIN_COMPILE_SDK_EXTENSION_PROPERTY=0
                |$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=1.0.0
                |"""
                .trimMargin()
                .toByteArray()
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(file).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }

    @Test
    fun testDsl() {
         val expectedAarMetadataBytes =
             """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=27
                |$MIN_COMPILE_SDK_EXTENSION_PROPERTY=2
                |$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=3.0.0
                |"""
                 .trimMargin()
                 .toByteArray()
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig {
                        aarMetadata {
                            minCompileSdk 27
                            minAgpVersion '3.0.0'
                            minCompileSdkExtension 2
                        }
                    }
                }
                """.trimIndent()
        )
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(file).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }

    @Test
    fun testDsl_productFlavor() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=28
                |$MIN_COMPILE_SDK_EXTENSION_PROPERTY=3
                |$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=3.1.0
                |"""
                .trimMargin()
                .toByteArray()
        // We add minCompileSdkVersion to defaultConfig and a product flavor to ensure that the
        // product flavor value trumps the defaultConfig value.
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig {
                        aarMetadata {
                            minCompileSdk 27
                            minAgpVersion '3.0.0'
                            minCompileSdkExtension 2
                        }
                    }
                    flavorDimensions 'foo'
                    productFlavors {
                        premium {
                            aarMetadata {
                                minCompileSdk 28
                                minAgpVersion '3.1.0'
                                minCompileSdkExtension 3
                            }
                        }
                    }
                }
                """.trimIndent()
        )
        project.executor().run(":lib:assemblePremiumDebug")
        project.getSubproject("lib").withAar(listOf("premium", "debug")) {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(file).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }

    @Test
    fun testDsl_buildType() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=29
                |$MIN_COMPILE_SDK_EXTENSION_PROPERTY=4
                |$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=3.2.0
                |"""
                .trimMargin()
                .toByteArray()
        // We add minCompileSdkVersion to defaultConfig, a product flavor, and the debug build
        // type to ensure that the build type value trumps the other values.
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig {
                        aarMetadata {
                            minCompileSdk 27
                            minAgpVersion '3.0.0'
                            minCompileSdkExtension 2
                        }
                    }
                    flavorDimensions 'foo'
                    productFlavors {
                        premium {
                            aarMetadata {
                                minCompileSdk 28
                                minAgpVersion '3.1.0'
                                minCompileSdkExtension 3
                            }
                        }
                    }
                    buildTypes {
                        debug {
                            aarMetadata {
                                minCompileSdk 29
                                minAgpVersion '3.2.0'
                                minCompileSdkExtension 4
                            }
                        }
                    }
                }
                """.trimIndent()
        )
        project.executor().run(":lib:assemblePremiumDebug")
        project.getSubproject("lib").withAar(listOf("premium", "debug")) {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(file).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }

    @Test
    fun testVariantApi() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=27
                |$MIN_COMPILE_SDK_EXTENSION_PROPERTY=2
                |$MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=3.0.0
                |"""
                .trimMargin()
                .toByteArray()
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig {
                        aarMetadata {
                            minCompileSdk 26
                            minAgpVersion '2.0.0'
                            minCompileSdkExtension 1
                        }
                    }
                }
                androidComponents {
                    onVariants(selector().all(), {
                        aarMetadata.minCompileSdk.set(27)
                        aarMetadata.minAgpVersion.set("3.0.0")
                        aarMetadata.minCompileSdkExtension.set(2)
                    })
                }
                """.trimIndent()
        )
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.AAR_METADATA_ENTRY_PATH)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(file).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }

    @Test
    fun testCompileSdkPreview() {
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    compileSdkPreview 'TiramisuPrivacySandbox'
                }
            """.trimIndent()
        )
        project.executor().run(":lib:writeDebugAarMetadata")
        val aarMetadataFile =
            FileUtils.join(
                project.getSubproject("lib").buildDir,
                SdkConstants.FD_INTERMEDIATES,
                InternalArtifactType.AAR_METADATA.getFolderName(),
                "debug",
                AarMetadataTask.AAR_METADATA_FILE_NAME
            )
        PathSubject.assertThat(aarMetadataFile).contains(
            "${FORCE_COMPILE_SDK_PREVIEW_PROPERTY}=TiramisuPrivacySandbox"
        )
    }
}
