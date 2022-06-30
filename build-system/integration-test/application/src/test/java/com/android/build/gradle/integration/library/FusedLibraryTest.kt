/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

@RunWith(Parameterized::class)
class FusedLibraryTest(
        private val includePublishing: Boolean,
) {

    companion object {
        @Parameterized.Parameters(name = "include_publishing = {0}")
        @JvmStatic
        fun data() = listOf(
                true,
                false,
        )
    }

    private val androidLib1 = MinimalSubProject.lib("com.example.androidLib1").also {
        it.appendToBuild("""
        dependencies {
            implementation 'junit:junit:4.12'
        }
        """.trimIndent())
        it.addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="string_from_android_lib_1">androidLib2</string>
              </resources>"""
        )
    }
    private val androidLib2 = MinimalSubProject.lib("com.example.androidLib2")

    private val testAar = generateAarWithContent("com.remotedep.remoteaar",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="remote_string">Remote String</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
            )

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library("com.remotedep:remoteaar:1", "aar", testAar)
            )
    )

    private val fusedLibrary = MinimalSubProject.fusedLibrary("com.example.fusedLib1").also {
        if (includePublishing) {
            it.appendToBuild("""
                apply plugin: 'maven-publish'

                dependencies {
                    include project(":androidLib1")
                    include project(":androidLib2")
                    include 'com.remotedep:remoteaar:1'
                }
                """.trimIndent())
        }
        it.appendToBuild(
                """
                android {
                    minSdk = $DEFAULT_MIN_SDK_VERSION
                }
                """.trimIndent()
        )
    }

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject.builder()
                            .subproject("androidLib1", androidLib1)
                            .subproject("androidLib2", androidLib2)
                            .subproject("fusedLib1", fusedLibrary)
                            .build()
            )
            .withAdditionalMavenRepo(mavenRepo)
            .create()

    @Test
    fun test() {
        if (includePublishing) {
            project
                    .execute("generatePomFileForMavenPublication", "generateMetadataFileForMavenPublication")
        } else {
            project.execute(":fusedLib1:assemble")
        }
        val fusedLib1BuildDir = project.getSubproject(":fusedLib1").buildDir
        File(fusedLib1BuildDir, "bundle/bundle.aar").also { aarFile ->
            println("Testing ${aarFile.absolutePath}")
            Truth.assertThat(aarFile.exists()).isTrue()
            if (includePublishing) {
                ZipFile(aarFile).use { aarZip ->
                    val valuesXml = aarZip.getEntry("res/values/values.xml")
                    Truth.assertThat(valuesXml).isNotNull()
                    Truth.assertThat(valuesXml.size).isGreaterThan(0)
                }
            }
        }
        if (includePublishing) {
            File(fusedLib1BuildDir, "publications/maven").also { publicationDir ->
                Truth.assertThat(File(publicationDir, "pom-default.xml").exists()).isTrue()
                Truth.assertThat(File(publicationDir, "module.json").exists()).isTrue()
            }
        }
    }
}
