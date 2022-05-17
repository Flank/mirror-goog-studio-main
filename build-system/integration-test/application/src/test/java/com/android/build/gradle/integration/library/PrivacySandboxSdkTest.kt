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

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.apk.Dex
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

class PrivacySandboxSdkTest {

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
        it.addFile(
            "src/main/java/com/example/androidlib1/Example.java",
            // language=java
            """
                package com.example.androidlib1;

                class Example {

                    public Example() {}

                    public void f1() {}
                }
            """.trimIndent()
        )
    }
    private val androidLib2 = MinimalSubProject.lib("com.example.androidLib2").also {
        it.addFile(
            "src/main/java/com/example/androidlib2/Example.java",
            // language=java
            """
                package com.example.androidlib2;

                class Example {

                    public Example() {}

                    public void f2() {}
                }
            """.trimIndent()
        )
    }

    private val privacySandboxSdk = MinimalSubProject.privacySandboxSdk("com.example.sdkLib1").also {
        it.appendToBuild(
            """
                dependencies {
                    include project(":androidLib1")
                    include project(":androidLib2")
                }

                android {
                    compileSdk = $DEFAULT_COMPILE_SDK_VERSION
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
                .subproject("sdkLib1", privacySandboxSdk)
                .build()
        ).create()

    @Test
    fun smokeTest() {
        project.execute(":sdkLib1:assemble")
        val sdkLib1BuildDir = project.getSubproject(":sdkLib1").buildDir
        assertThat(sdkLib1BuildDir.resolve("packageJar/classes.jar").isFile).isTrue()
    }

    @Test
    fun testPublishing() {
        project.getSubproject("sdkLib1").buildFile.appendText("""
            apply plugin: 'maven-publish'
        """.trimIndent())

        project.executor().run(":sdkLib1:assemble", "generatePomFileForMavenPublication", "generateMetadataFileForMavenPublication")
        val sdkLib1BuildDir = project.getSubproject(":sdkLib1").buildDir
        assertThat(sdkLib1BuildDir.resolve("packageJar/classes.jar").isFile).isTrue()
        sdkLib1BuildDir.resolve("publications/maven").also { publicationDir ->
            assertThat(File(publicationDir, "pom-default.xml").isFile).isTrue()
            assertThat(File(publicationDir, "module.json").isFile).isTrue()
        }
    }

    @Test
    fun testDexing() {
        project.execute(":sdkLib1:mergeDex")

        val dex = Dex(
            FileUtils.join(
                project.getSubproject(":sdkLib1").intermediatesDir,
                "dex",
                "single",
                "classes.dex"
            )
        )

        assertThat(dex.classes.keys).containsAtLeastElementsIn(
            listOf(
                "Lcom/example/androidlib1/Example;",
                "Lcom/example/androidlib2/Example;",
            )
        )

        assertThat(dex.classes["Lcom/example/androidlib1/Example;"]!!.methods.map {
            it.name
        }).contains("f1")

        assertThat(dex.classes["Lcom/example/androidlib2/Example;"]!!.methods.map {
            it.name
        }).contains("f2")
    }
}
