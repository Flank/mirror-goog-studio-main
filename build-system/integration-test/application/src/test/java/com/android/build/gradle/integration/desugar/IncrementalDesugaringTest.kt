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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.PROJECT_DEX_ARCHIVE
import com.android.build.gradle.internal.scope.getOutputDir
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests that dexing with desugaring is incremental (only a minimal number of class files are
 * re-desugared).
 *
 * Note that this test is focused on the incrementality aspect. The correctness aspect should be
 * tested by other tests.
 */
@RunWith(FilterableParameterized::class)
class IncrementalDesugaringTest(
    @Suppress("UNUSED_PARAMETER") withIncrementalDesugaringV2: Boolean
) {

    companion object {

        @Parameterized.Parameters(name = "incrementalDesugaringV2_{0}")
        @JvmStatic
        fun parameters() = listOf(false) // TODO Add a test for incrementalDesugaringV2
    }

    @get:Rule
    var project = EmptyActivityProjectBuilder().also { it.withUnitTest = false }.build()

    // Java source files
    private lateinit var interfaceWithDefaultMethodJavaFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodJavaFile: File
    private lateinit var mainActivityJavaFile: File

    // Compiled class files
    private lateinit var interfaceWithDefaultMethodClassFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodClassFile: File
    private lateinit var mainActivityClassFile: File

    // Output dex files
    private lateinit var interfaceWithDefaultMethodDexFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodDexFile: File
    private lateinit var mainActivityDexFile: File

    private lateinit var incrementalTestHelper: IncrementalTestHelper

    @Before
    fun setUp() {
        val app = project.getSubproject("app")

        val interfaceWithDefaultMethodPath = "com/example/myapplication/InterfaceWithDefaultMethod"
        val classUsingInterfaceWithDefaultMethod = "com/example/myapplication/ClassUsingInterfaceWithDefaultMethod"
        val mainActivityPath = "com/example/myapplication/MainActivity"

        interfaceWithDefaultMethodJavaFile = File("${app.mainSrcDir}/$interfaceWithDefaultMethodPath.java")
        classUsingInterfaceWithDefaultMethodJavaFile = File("${app.mainSrcDir}/$classUsingInterfaceWithDefaultMethod.java")
        mainActivityJavaFile = File("${app.mainSrcDir}/$mainActivityPath.java")

        interfaceWithDefaultMethodClassFile = JAVAC.getOutputDir(app.buildDir).resolve("debug/classes/$interfaceWithDefaultMethodPath.class")
        classUsingInterfaceWithDefaultMethodClassFile = JAVAC.getOutputDir(app.buildDir).resolve("debug/classes/$classUsingInterfaceWithDefaultMethod.class")
        mainActivityClassFile = JAVAC.getOutputDir(app.buildDir).resolve("debug/classes/$mainActivityPath.class")

        interfaceWithDefaultMethodDexFile = PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir).resolve("debug/out/$interfaceWithDefaultMethodPath.dex")
        classUsingInterfaceWithDefaultMethodDexFile = PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir).resolve("debug/out/$classUsingInterfaceWithDefaultMethod.dex")
        mainActivityDexFile = PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir).resolve("debug/out/$mainActivityPath.dex")

        TestFileUtils.appendToFile(
            app.buildFile,
            """
            android.compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_8
                targetCompatibility JavaVersion.VERSION_1_8
            }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            interfaceWithDefaultMethodJavaFile,
            """
            package com.example.myapplication;

            interface InterfaceWithDefaultMethod {

                default void defaultMethod() {
                    System.out.println("Hello from default method!");
                }

                void nonDefaultMethod();
            }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            classUsingInterfaceWithDefaultMethodJavaFile,
            """
            package com.example.myapplication;

            class ClassUsingInterfaceWithDefaultMethod implements InterfaceWithDefaultMethod {

                @Override
                public void nonDefaultMethod() {
                    System.out.println("Hello from non-default method!");
                }
            }
            """.trimIndent()
        )

        incrementalTestHelper = IncrementalTestHelper(
            executor = project.executor(),
            filesToTrackChanges = setOf(
                interfaceWithDefaultMethodClassFile,
                classUsingInterfaceWithDefaultMethodClassFile,
                mainActivityClassFile,
                interfaceWithDefaultMethodDexFile,
                classUsingInterfaceWithDefaultMethodDexFile,
                mainActivityDexFile
            )
        )
    }

    @Test
    fun `change interface with default method, with a method signature change`() {
        incrementalTestHelper
            .runFullBuild()
            .applyChange {
                TestFileUtils.searchAndReplace(
                    interfaceWithDefaultMethodJavaFile,
                    "default void defaultMethod() {",
                    "default void defaultMethodWithChangedSignature() {"
                )
            }
            .runIncrementalBuild()
            .assertFileChanges(
                filesWithChangedTimestampsAndContents = setOf(
                    interfaceWithDefaultMethodClassFile,
                    interfaceWithDefaultMethodDexFile,
                    classUsingInterfaceWithDefaultMethodDexFile
                ),
                filesWithChangedTimestampsButNotContents = setOf(
                    classUsingInterfaceWithDefaultMethodClassFile
                ),
                filesWithUnchangedTimestampsAndContents = setOf(
                    mainActivityClassFile,
                    mainActivityDexFile
                )
            )
    }

    @Test
    fun `change interface with default method, with a method body change`() {
       incrementalTestHelper
            .runFullBuild()
            .applyChange {
                TestFileUtils.searchAndReplace(
                    interfaceWithDefaultMethodJavaFile,
                    "System.out.println(\"Hello from default method!\")",
                    "System.out.println(\"Hello from default method, with changed method body!\")"
                )
            }
            .runIncrementalBuild()
            .assertFileChanges(
                filesWithChangedTimestampsAndContents = setOf(
                    interfaceWithDefaultMethodClassFile,
                    interfaceWithDefaultMethodDexFile
                ),
                filesWithChangedTimestampsButNotContents = setOf(
                    classUsingInterfaceWithDefaultMethodClassFile,
                    classUsingInterfaceWithDefaultMethodDexFile
                ),
                filesWithUnchangedTimestampsAndContents = setOf(
                    mainActivityClassFile,
                    mainActivityDexFile
                )
            )
    }

    @Test
    fun `change interface with default method, with a comment change`() {
        incrementalTestHelper
            .runFullBuild()
            .applyChange {
                TestFileUtils.searchAndReplace(
                    interfaceWithDefaultMethodJavaFile,
                    "default void defaultMethod() {",
                    "default void defaultMethod() { // This is a comment-only change"
                )
            }
            .runIncrementalBuild()
            .assertFileChanges(
                filesWithChangedTimestampsAndContents = setOf(
                ),
                filesWithChangedTimestampsButNotContents = setOf(
                    interfaceWithDefaultMethodClassFile,
                    classUsingInterfaceWithDefaultMethodClassFile
                ),
                filesWithUnchangedTimestampsAndContents = setOf(
                    interfaceWithDefaultMethodDexFile,
                    classUsingInterfaceWithDefaultMethodDexFile,
                    mainActivityClassFile,
                    mainActivityDexFile
                )
            )
    }
}