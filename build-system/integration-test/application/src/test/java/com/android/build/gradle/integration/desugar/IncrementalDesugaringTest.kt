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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.ChangeType.CHANGED
import com.android.build.gradle.integration.common.utils.ChangeType.CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS
import com.android.build.gradle.integration.common.utils.ChangeType.UNCHANGED
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.PROJECT_DEX_ARCHIVE
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
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
    private val changeScope: ChangeScope,
    private val withIncrementalDesugaringV2: Boolean
) {

    companion object {

        @Parameterized.Parameters(name = "changeScope_{0}_incrementalDesugaringV2_{1}")
        @JvmStatic
        fun parameters() = listOf(
            // incrementalDesugaringV2 currently takes effect on app only
            arrayOf(ChangeScope.APP, true),
            arrayOf(ChangeScope.APP, false),
            arrayOf(
                ChangeScope.ANDROID_LIB,
                BooleanOption.ENABLE_INCREMENTAL_DESUGARING_V2.defaultValue
            ),
            arrayOf(
                ChangeScope.JAVA_LIB,
                BooleanOption.ENABLE_INCREMENTAL_DESUGARING_V2.defaultValue
            )
        )
    }

    @get:Rule
    var project = EmptyActivityProjectBuilder()
        .also { it.withUnitTest = false }
        .addAndroidLibrary()
        .addJavaLibrary()
        .build()

    // Java source files
    private lateinit var interfaceWithDefaultMethodJavaFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodJavaFile: File
    private lateinit var mainActivityJavaFile: File

    // Compiled class files
    private lateinit var interfaceWithDefaultMethodClassFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodClassFile: File
    private lateinit var mainActivityClassFile: File

    // Published class files (from libraries), `null` for app
    private var interfaceWithDefaultMethodPublishedClassFile: File? = null
    private var classUsingInterfaceWithDefaultMethodPublishedClassFile: File? = null

    // Output dex files
    private lateinit var interfaceWithDefaultMethodDexFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodDexFile: File
    private lateinit var mainActivityDexFile: File

    private lateinit var incrementalTestHelper: IncrementalTestHelper

    @Before
    fun setUp() {
        val app = project.getSubproject("app")
        val androidLib = project.getSubproject("lib")
        val javaLib = project.getSubproject("javalib")

        // The subproject (and its packageName) where we will apply a change.
        val subproject: GradleTestProject
        val packageName: String
        when (changeScope) {
            ChangeScope.APP -> {
                subproject = app
                packageName = "com.example.myapplication"
            }
            ChangeScope.ANDROID_LIB -> {
                subproject = androidLib
                packageName = "com.example.lib"

            }
            ChangeScope.JAVA_LIB -> {
                subproject = javaLib
                packageName = "com.example.javalib"
            }
        }
        val packagePath = packageName.replace('.', '/')

        val interfaceWithDefaultMethodPath = "$packagePath/InterfaceWithDefaultMethod"
        val classUsingInterfaceWithDefaultMethodPath =
            "$packagePath/ClassUsingInterfaceWithDefaultMethod"
        val mainActivityPath = "com/example/myapplication/MainActivity"

        interfaceWithDefaultMethodJavaFile =
            File("${subproject.mainSrcDir}/$interfaceWithDefaultMethodPath.java")
        classUsingInterfaceWithDefaultMethodJavaFile =
            File("${subproject.mainSrcDir}/$classUsingInterfaceWithDefaultMethodPath.java")
        mainActivityJavaFile = File("${app.mainSrcDir}/$mainActivityPath.java")

        // Add dependencies from app to subprojects
        when (changeScope) {
            ChangeScope.APP -> { /* Nothing */
            }
            ChangeScope.ANDROID_LIB -> {
                TestFileUtils.appendToFile(
                    app.buildFile,
                    """
                    dependencies {
                        implementation project(":lib")
                    }
                    """.trimIndent()
                )
            }
            ChangeScope.JAVA_LIB -> {
                TestFileUtils.appendToFile(
                    app.buildFile,
                    """
                    dependencies {
                        implementation project(":javalib")
                    }
                    """.trimIndent()
                )
            }
        }

        TestFileUtils.appendToFile(
            app.buildFile,
            """
            android.compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_8
                targetCompatibility JavaVersion.VERSION_1_8
            }
            """.trimIndent()
        )
        if (changeScope == ChangeScope.ANDROID_LIB) {
            TestFileUtils.appendToFile(
                subproject.buildFile,
                """
                android.compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_8
                    targetCompatibility JavaVersion.VERSION_1_8
                }
                """.trimIndent()
            )
        }

        interfaceWithDefaultMethodJavaFile.parentFile.mkdirs()
        interfaceWithDefaultMethodJavaFile.writeText(
            """
            package $packageName;

            interface InterfaceWithDefaultMethod {

                default void defaultMethod() {
                    System.out.println("Hello from default method!");
                }

                void nonDefaultMethod();
            }
            """.trimIndent()
        )

        classUsingInterfaceWithDefaultMethodJavaFile.parentFile.mkdirs()
        classUsingInterfaceWithDefaultMethodJavaFile.writeText(
            """
            package $packageName;

            class ClassUsingInterfaceWithDefaultMethod implements InterfaceWithDefaultMethod {

                @Override
                public void nonDefaultMethod() {
                    System.out.println("Hello from non-default method!");
                }
            }
            """.trimIndent()
        )

        when (changeScope) {
            ChangeScope.APP, ChangeScope.ANDROID_LIB -> {
                interfaceWithDefaultMethodClassFile = JAVAC.getOutputDir(subproject.buildDir)
                    .resolve("debug/classes/$interfaceWithDefaultMethodPath.class")
                classUsingInterfaceWithDefaultMethodClassFile =
                    JAVAC.getOutputDir(subproject.buildDir)
                        .resolve("debug/classes/$classUsingInterfaceWithDefaultMethodPath.class")
            }
            ChangeScope.JAVA_LIB -> {
                interfaceWithDefaultMethodClassFile =
                    subproject.buildDir.resolve("classes/java/main/$interfaceWithDefaultMethodPath.class")
                classUsingInterfaceWithDefaultMethodClassFile =
                    subproject.buildDir.resolve("classes/java/main/$classUsingInterfaceWithDefaultMethodPath.class")
            }
        }
        mainActivityClassFile =
            JAVAC.getOutputDir(app.buildDir).resolve("debug/classes/$mainActivityPath.class")

        when (changeScope) {
            ChangeScope.APP -> {
                interfaceWithDefaultMethodPublishedClassFile = null
                classUsingInterfaceWithDefaultMethodPublishedClassFile = null
            }
            ChangeScope.ANDROID_LIB -> {
                // Classes are currently published to a jar instead of a directory. (This will
                // change when bug 132615827 is fixed.)
                interfaceWithDefaultMethodPublishedClassFile =
                    RUNTIME_LIBRARY_CLASSES.getOutputDir(subproject.buildDir)
                        .resolve("debug/classes.jar")
                classUsingInterfaceWithDefaultMethodPublishedClassFile =
                    RUNTIME_LIBRARY_CLASSES.getOutputDir(subproject.buildDir)
                        .resolve("debug/classes.jar")
            }
            ChangeScope.JAVA_LIB -> {
                interfaceWithDefaultMethodPublishedClassFile = interfaceWithDefaultMethodClassFile
                classUsingInterfaceWithDefaultMethodPublishedClassFile =
                    classUsingInterfaceWithDefaultMethodClassFile
            }
        }

        when (changeScope) {
            ChangeScope.APP -> {
                interfaceWithDefaultMethodDexFile =
                    PROJECT_DEX_ARCHIVE.getOutputDir(subproject.buildDir)
                        .resolve("debug/out/$interfaceWithDefaultMethodPath.dex")
                classUsingInterfaceWithDefaultMethodDexFile =
                    PROJECT_DEX_ARCHIVE.getOutputDir(subproject.buildDir)
                        .resolve("debug/out/$classUsingInterfaceWithDefaultMethodPath.dex")
            }
            ChangeScope.ANDROID_LIB -> {
                val dexOutputDir = findDexTransformOutputDir(subproject.buildDir).resolve("classes")
                // Since classes are currently published to a jar instead of a directory, the dex
                // output is currently 1 dex file containing all the classes. (This will change when
                // bug 132615827 is fixed.)
                interfaceWithDefaultMethodDexFile = dexOutputDir.resolve("classes.dex")
                classUsingInterfaceWithDefaultMethodDexFile = dexOutputDir.resolve("classes.dex")
            }
            ChangeScope.JAVA_LIB -> {
                val dexOutputDir =
                    findDexTransformOutputDir(subproject.buildDir).resolve("jetified-javalib")
                // Since classes are currently published to a jar instead of a directory, the dex
                // output is currently 1 dex file containing all the classes. (This will change when
                // bug 132615827 is fixed.)
                interfaceWithDefaultMethodDexFile = dexOutputDir.resolve("classes.dex")
                classUsingInterfaceWithDefaultMethodDexFile = dexOutputDir.resolve("classes.dex")
            }
        }
        mainActivityDexFile = PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir)
            .resolve("debug/out/$mainActivityPath.dex")

        incrementalTestHelper = IncrementalTestHelper(
            executor = project.executor().with(
                BooleanOption.ENABLE_INCREMENTAL_DESUGARING_V2,
                withIncrementalDesugaringV2
            ),
            buildTask = ":app:mergeDexDebug",
            filesToTrackChanges = setOf(
                interfaceWithDefaultMethodClassFile,
                classUsingInterfaceWithDefaultMethodClassFile,
                mainActivityClassFile,
                interfaceWithDefaultMethodDexFile,
                classUsingInterfaceWithDefaultMethodDexFile,
                mainActivityDexFile
            ) + (if (changeScope == ChangeScope.APP) {
                emptySet()
            } else {
                setOf(
                    interfaceWithDefaultMethodPublishedClassFile!!,
                    classUsingInterfaceWithDefaultMethodPublishedClassFile!!
                )
            })
        )
    }

    /**
     * Finds the output directory of the dexing transform using heuristics. We can't hard-code the
     * path because it contains Gradle hashes.
     *
     * For example, given this directory structure:
     * `../build/.transforms/16e674a220d3c8cee4f318266cc44626/classes/classes.dex`, this method
     * returns `../build/.transforms/16e674a220d3c8cee4f318266cc44626`.
     */
    private fun findDexTransformOutputDir(buildDir: File): File {
        // Run a full build so that dex outputs are generated, then we will try to locate them.
        project.executor().with(
            BooleanOption.ENABLE_INCREMENTAL_DESUGARING_V2,
            withIncrementalDesugaringV2
        ).run("clean", ":app:mergeDexDebug")

        val containsDexFiles: (File) -> Boolean = { file ->
            file.listFiles()?.any {
                it.extension.equals("dex", ignoreCase = true)
            } ?: false
        }
        val dexOutputDirs = buildDir.resolve(".transforms").listFiles()!!.filter {
            it.listFiles()?.let { files ->
                files.size == 1 && containsDexFiles(files[0])
            } ?: false
        }

        check(dexOutputDirs.isNotEmpty()) { "Can't find dex files in `$buildDir`" }
        check(dexOutputDirs.size == 1) {
            "Expected 1 dex output dir but found multiple ones: " +
                    dexOutputDirs.joinToString(", ", transform = { it.path })
        }
        return dexOutputDirs[0]
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

        when (changeScope) {
            ChangeScope.APP -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.ANDROID_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.JAVA_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
        }
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

        when (changeScope) {
            ChangeScope.APP -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.ANDROID_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.JAVA_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to CHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
        }
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

        when (changeScope) {
            ChangeScope.APP -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to UNCHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to UNCHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.ANDROID_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to UNCHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to UNCHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to UNCHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
            ChangeScope.JAVA_LIB -> {
                incrementalTestHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        mainActivityClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to UNCHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to UNCHANGED,
                        mainActivityDexFile to UNCHANGED
                    )
                )
            }
        }
    }

    /** The subproject where we apply a change. */
    enum class ChangeScope {
        APP,
        ANDROID_LIB,
        JAVA_LIB
    }
}