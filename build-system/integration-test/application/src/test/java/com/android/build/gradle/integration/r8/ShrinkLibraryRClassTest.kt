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

package com.android.build.gradle.integration.r8

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.truth.AarSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

/** Regression test for https://buganizer.corp.google.com/issues/170922353 */
class ShrinkLibraryRClassTest {

    @get:Rule
    val project: GradleTestProject

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    init {
        val lib = MinimalSubProject.lib("com.example.lib")
            .withFile(
                    "src/main/res/values/strings.xml",
                    //language=xml
                    """
                        <resources>
                            <string name="lib_string">lib string</string>
                        </resources>
                    """.trimIndent())
            .withFile(
                    "src/main/java/com/example/lib/UseR.java",
                    //language=java
                    """
                        package com.example.lib;

                        public class UseR {
                            public static int getLibStringValue() {
                                return R.string.lib_string;
                            }
                        }
                    """.trimIndent())
            .withFile("proguard-rules.pro", """

                -keep class com.example.lib.UseR {
                    public static int getLibStringValue();
                }

            """.trimIndent())
            .appendToBuild(
                    //language=groovy
                    """
                    android {
                        buildTypes {
                            release {
                                minifyEnabled true
                                proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
                            }
                        }
                    }
                    """.trimIndent())

        project = GradleTestProject.builder().fromTestApp(lib).create()
    }

    @Test
    fun testRClassEntriesNotInlined() {
        project.executor().run(":assembleRelease")

        val jarFromAar = extractAarJar()
        val exampleRuntimeLibRClass = createExampleRuntimeLibRClass()

        // Run the shrunk method against a runtime lib r class to check the reference is still present.
        URLClassLoader(arrayOf(jarFromAar.toUri().toURL(), exampleRuntimeLibRClass.toUri().toURL())).use { classLoader ->
            val usesR = classLoader.loadClass("com.example.lib.UseR")
            val method = usesR.getDeclaredMethod("getLibStringValue")
            val libStringValue = method.invoke(null) as Int
            assertThat(libStringValue).isEqualTo(99)
        }
    }

    private fun createExampleRuntimeLibRClass(): Path {
        val symbols = SymbolTable.builder()
                .tablePackage("com.example.lib")
                .add(Symbol.normalSymbol(ResourceType.STRING, "lib_string", intValue = 99))
                .build()
        val jar = temporaryFolder.newFolder().toPath().resolve("extractedJar.jar")
        exportToCompiledJava(listOf(symbols), jar, true)
        return jar
    }

    private fun extractAarJar(): Path {
        val jar = temporaryFolder.newFolder().toPath().resolve("extractedJar.jar")
        project.getAar("release") { aar ->
            assertThat(aar).containsClass("Lcom/example/lib/UseR;")
            Files.copy(aar.getEntry("classes.jar")!!, jar)
        }
        return jar
    }

}
