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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.fail

class CoreLibraryDesugarGeneralizationTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPluginWithMinSdkVersion("com.android.application",21))
        .create()

    @Before
    fun setUp() {
        val desugarLib = project.projectDir.toPath().resolve(DESUGAR_LIB_JAR)
        TestInputsGenerator.jarWithEmptyClasses(desugarLib, listOf("test/A"))

        val desugarConfig = project.projectDir.toPath().resolve(DESUGAR_CONFIG_JAR)
        BufferedOutputStream(Files.newOutputStream(desugarConfig)).use { outputStream ->
            ZipOutputStream(outputStream).use { zipOutputStream ->
                val entry = ZipEntry("META-INF/desugar/d8/desugar.json")
                zipOutputStream.putNextEntry(entry)
                zipOutputStream.write(DESUGAR_CONFIG_CONTENT.toByteArray(StandardCharsets.UTF_8))
                zipOutputStream.closeEntry()
            }
        }

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                }
                android.defaultConfig.multiDexEnabled = true
                dependencies {
                    coreLibraryDesugaring files('$DESUGAR_LIB_JAR')
                    coreLibraryDesugaring files('$DESUGAR_CONFIG_JAR')
                }
            """.trimIndent())
    }

    @Test
    fun testNonMinifyDebugBuild() {
        project.executor().run("assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        val desugarDex = getDexWithSpecificClass(desugarClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $desugarClass")
        DexSubject.assertThat(desugarDex).doesNotContainClasses(programClass)
    }

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    private val desugarClass = "Lfoo$/A;"
    private val programClass = "Lcom/example/helloworld/HelloWorld;"

    companion object {
        private const val DESUGAR_LIB_JAR = "desugar-lib.jar"
        private const val DESUGAR_CONFIG_JAR = "desugar-config.jar"
        private const val DESUGAR_CONFIG_CONTENT = """
            {
               "artifact_id": "test",
               "configuration_format_version": 4,
               "group_id": "com.example",
               "required_compilation_api_level": 26,
               "synthesized_library_classes_package_prefix": "foo$.",
               "library_flags": [
                    {
                        "api_level_below_or_equal": 25,
                        "rewrite_prefix": {
                            "test.A": "foo$.A"
                        }
                    }
                ],
                "program_flags": [],
                "common_flags": [],
                "version": "1.1.5"
            }
        """
    }
}