/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.internal.tasks.AarMetadataReader
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Tests for [FusedLibraryMergeArtifactTask] */
internal class FusedLibraryMergeArtifactTaskTest {

    @JvmField
    @Rule
    val project = createGradleProject {
        // Library dependency at depth 1 with no dependencies.
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                minSdk = 12
                aarMetadata {
                    minCompileSdk = 12
                    minAgpVersion = "3.0.0"
                    minCompileSdkExtension = 2
                }
            }
        }
        // Library dependency at depth 0 with a dependency on androidLib1.
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
                minSdk = 19
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        // Library dependency at depth 0 with no dependencies
        subProject(":androidLib3") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib3"
                minSdk = 18
                aarMetadata {
                    minCompileSdk = 18
                    minAgpVersion = "4.0.1"
                }
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            android {
                namespace = "com.example.fusedLib1"
                minSdk = 19
            }
            dependencies {
                include(project(":androidLib3"))
                include(project(":androidLib2"))
            }
        }
    }

    @Test
    fun testAarMetadataMerging() {
        val fusedLibraryAar = getFusedLibraryAar()
        fusedLibraryAar?.let { aarFile ->
            ZipFile(aarFile).use {
                val mergedAarMetadata =
                        it.getEntry("META-INF/com/android/build/gradle/aar-metadata.properties")
                assertThat(mergedAarMetadata).isNotNull()
                val metadataContents = it.getInputStream(mergedAarMetadata)
                val aarMetadataReader = AarMetadataReader(metadataContents)
                // Value constant from AGP
                assertThat(aarMetadataReader.aarFormatVersion).isEqualTo("1.0")
                // Value constant from AGP
                assertThat(aarMetadataReader.aarMetadataVersion).isEqualTo("1.0")
                // Value from androidLib3
                assertThat(aarMetadataReader.minAgpVersion).isEqualTo("4.0.1")
                // Value from androidLib3
                assertThat(aarMetadataReader.minCompileSdk).isEqualTo("18")
                // Value from androidLib1
                assertThat(aarMetadataReader.minCompileSdkExtension).isEqualTo("2")
            }
        }
    }

    private fun getFusedLibraryAar(): File? {
        project.execute(":fusedLib1:bundle")
        val fusedLib1 = project.getSubproject("fusedLib1")
        return FileUtils.join(fusedLib1.bundleDir, "bundle.aar")
    }
}
