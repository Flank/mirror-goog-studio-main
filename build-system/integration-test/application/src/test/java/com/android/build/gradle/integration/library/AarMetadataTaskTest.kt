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

import com.android.SdkConstants.AAR_FORMAT_VERSION_PROPERTY
import com.android.SdkConstants.AAR_METADATA_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.apksig.internal.util.ByteBufferUtils.toByteArray
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.tasks.AarMetadataTask
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
                |"""
                .trimMargin()
                .replace("\n", System.lineSeparator())
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
    fun testMinCompileSdkVersion() {
         val expectedAarMetadataBytes =
             """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=27
                |"""
                 .trimMargin()
                 .replace("\n", System.lineSeparator())
                 .toByteArray()
        project.getSubproject("lib").buildFile.appendText(
            "android.defaultConfig.aarMetadata.minCompileSdk 27"
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
    fun testMinCompileSdkVersion_productFlavor() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=28
                |"""
                .trimMargin()
                .replace("\n", System.lineSeparator())
                .toByteArray()
        // We add minCompileSdkVersion to defaultConfig and a product flavor to ensure that the
        // product flavor value trumps the defaultConfig value.
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig.aarMetadata.minCompileSdk 27
                    flavorDimensions 'foo'
                    productFlavors {
                        premium {
                            aarMetadata.minCompileSdk 28
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
    fun testMinCompileSdkVersion_buildType() {
        val expectedAarMetadataBytes =
            """
                |$AAR_FORMAT_VERSION_PROPERTY=1.0
                |$AAR_METADATA_VERSION_PROPERTY=1.0
                |$MIN_COMPILE_SDK_PROPERTY=29
                |"""
                .trimMargin()
                .replace("\n", System.lineSeparator())
                .toByteArray()
        // We add minCompileSdkVersion to defaultConfig, a product flavor, and the debug build
        // type to ensure that the build type value trumps the other values.
        project.getSubproject("lib").buildFile.appendText(
            """
                android {
                    defaultConfig.aarMetadata.minCompileSdk 27
                    flavorDimensions 'foo'
                    productFlavors {
                        premium {
                            aarMetadata.minCompileSdk 28
                        }
                    }
                    buildTypes.debug.aarMetadata.minCompileSdk 29
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
}
