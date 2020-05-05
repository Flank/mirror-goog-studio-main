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

import com.android.apksig.internal.util.ByteBufferUtils.toByteArray
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.options.BooleanOption
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
        // first test that there is no AAR metadata file if the feature is not enabled.
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            assertThat(getEntry(AarMetadataTask.aarMetadataEntryPath)).isNull()
        }

        // then test that AAR metadata file is present if the feature is enabled.
        project.executor().with(BooleanOption.ENABLE_AAR_METADATA, true).run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            assertThat(getEntry(AarMetadataTask.aarMetadataEntryPath)).isNotNull()
        }
    }

    @Test
    fun testMinCompileSdkVersion() {
         val expectedAarMetadataBytes =
             """
                |aarMetadataVersion=1.0
                |aarVersion=1.0
                |minCompileSdk=27
                |"""
                 .trimMargin()
                 .replace("\n", System.lineSeparator())
                 .toByteArray()
        project.getSubproject("lib").buildFile.appendText(
            "android.defaultConfig.aarMetadata.minCompileSdk 27"
        )
        project.executor()
            .with(BooleanOption.ENABLE_AAR_METADATA, true)
            .run(":lib:assembleDebug")
        project.getSubproject("lib").withAar("debug") {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.aarMetadataEntryPath)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(this.file.toFile()).use { aar ->
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
                |aarMetadataVersion=1.0
                |aarVersion=1.0
                |minCompileSdk=28
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
        project.executor()
            .with(BooleanOption.ENABLE_AAR_METADATA, true)
            .run(":lib:assemblePremiumDebug")
        project.getSubproject("lib").withAar(listOf("premium", "debug")) {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.aarMetadataEntryPath)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(this.file.toFile()).use { aar ->
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
                |aarMetadataVersion=1.0
                |aarVersion=1.0
                |minCompileSdk=29
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
        project.executor()
            .with(BooleanOption.ENABLE_AAR_METADATA, true)
            .run(":lib:assemblePremiumDebug")
        project.getSubproject("lib").withAar(listOf("premium", "debug")) {
            val aarMetadataEntryPath = getEntry(AarMetadataTask.aarMetadataEntryPath)
            assertThat(aarMetadataEntryPath).isNotNull()
            ZipArchive(this.file.toFile()).use { aar ->
                val aarMetadataBytes =
                    toByteArray(aar.getContent(aarMetadataEntryPath.toString()))
                assertThat(aarMetadataBytes).isEqualTo(expectedAarMetadataBytes)
            }
        }
    }
}
