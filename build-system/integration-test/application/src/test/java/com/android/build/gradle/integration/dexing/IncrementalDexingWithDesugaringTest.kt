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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.COM_EXAMPLE_LIB
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.COM_EXAMPLE_MYAPPLICATION
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.JAVALIB
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.LIB
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.ChangeType.CHANGED
import com.android.build.gradle.integration.common.utils.ChangeType.CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS
import com.android.build.gradle.integration.common.utils.ChangeType.UNCHANGED
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.dexing.IncrementalDexingWithDesugaringTest.Scenario.ANDROID_LIB
import com.android.build.gradle.integration.dexing.IncrementalDexingWithDesugaringTest.Scenario.ANDROID_LIB_WITH_POST_JAVAC_CLASSES
import com.android.build.gradle.integration.dexing.IncrementalDexingWithDesugaringTest.Scenario.APP
import com.android.build.gradle.integration.dexing.IncrementalDexingWithDesugaringTest.Scenario.JAVA_LIB
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.PROJECT_DEX_ARCHIVE
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.testutils.TestInputsGenerator
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests that dexing with desugaring is incremental (only a minimal number of class files are
 * re-dexed).
 *
 * Note that this test is focused on the incrementality aspect. The correctness aspect should be
 * tested by other tests.
 */
@RunWith(FilterableParameterized::class)
class IncrementalDexingWithDesugaringTest(
    private val scenario: Scenario,
    private val withMinSdk24Plus: Boolean
) {

    companion object {

        @Parameterized.Parameters(
            name = "scenario_{0}_minSdk24Plus_{1}"
        )
        @JvmStatic
        fun parameters(): List<Array<Any>> {
            return listOf(
                arrayOf(APP, true),
                arrayOf(APP, false),
                arrayOf(ANDROID_LIB, true),
                arrayOf(ANDROID_LIB, false),
                arrayOf(ANDROID_LIB_WITH_POST_JAVAC_CLASSES, true),
                arrayOf(ANDROID_LIB_WITH_POST_JAVAC_CLASSES, false),
                arrayOf(JAVA_LIB, true),
                arrayOf(JAVA_LIB, false)
            )
        }

        private const val INTERFACE_WITH_DEFAULT_METHOD = "InterfaceWithDefaultMethod"
        private const val CLASS_USING_INTERFACE_WITH_DEFAULT_METHOD =
            "ClassUsingInterfaceWithDefaultMethod"
        private const val STAND_ALONE_CLASS = "StandAloneClass"
    }

    @get:Rule
    var project = EmptyActivityProjectBuilder()
        .also {
            if (withMinSdk24Plus) {
                it.minSdkVersion = 24
            } else {
                it.minSdkVersion = 21
            }
            it.withUnitTest = false
        }
        .addAndroidLibrary(
            addImplementationDependencyFromApp =
            scenario in setOf(ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES)
        )
        .addJavaLibrary(
            addImplementationDependencyFromApp = scenario == JAVA_LIB
        )
        .build()

    private lateinit var app: GradleTestProject
    private lateinit var androidLib: GradleTestProject
    private lateinit var javaLib: GradleTestProject

    // Java source files
    private lateinit var interfaceWithDefaultMethodJavaFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodJavaFile: File
    private lateinit var standAloneClassJavaFile: File

    // Compiled class files
    private lateinit var interfaceWithDefaultMethodClassFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodClassFile: File
    private lateinit var standAloneClassClassFile: File

    // Published class files (from libraries), `null` for app
    private var interfaceWithDefaultMethodPublishedClassFile: File? = null
    private var classUsingInterfaceWithDefaultMethodPublishedClassFile: File? = null
    private var standAloneClassPublishedClassFile: File? = null

    // Output dex files
    private lateinit var interfaceWithDefaultMethodDexFile: File
    private lateinit var classUsingInterfaceWithDefaultMethodDexFile: File
    private lateinit var standAloneClassDexFile: File

    private lateinit var incrementalTestHelper: IncrementalTestHelper

    /** Returns the subproject where we will apply a change. */
    private fun getSubproject() = when (scenario) {
        APP -> app
        ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> androidLib
        JAVA_LIB -> javaLib
    }

    /** Returns package name of the subproject where we will apply a change. */
    private fun getPackageName() = when (scenario) {
        APP -> COM_EXAMPLE_MYAPPLICATION
        ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> COM_EXAMPLE_LIB
        JAVA_LIB -> "com.example.javalib"
    }

    /** Returns package path of the subproject where we will apply a change. */
    private fun getPackagePath() = getPackageName().replace('.', '/')

    @Before
    fun setUp() {
        app = project.getSubproject(EmptyActivityProjectBuilder.APP)
        androidLib = project.getSubproject(LIB)
        javaLib = project.getSubproject(JAVALIB)

        // Add post-javac classes for the ANDROID_LIB_WITH_POST_JAVAC_CLASSES scenario
        if (scenario == ANDROID_LIB_WITH_POST_JAVAC_CLASSES) {
            TestInputsGenerator.jarWithEmptyClasses(
                androidLib.projectDir.resolve("post-javac-classes.jar").toPath(),
                setOf("post/javac/classes/SampleClass")
            )
            TestFileUtils.appendToFile(
                androidLib.buildFile,
                """
                android.libraryVariants.all { variant ->
                    variant.registerPostJavacGeneratedBytecode(project.files("post-javac-classes.jar"))
                }
                """.trimIndent()
            )
        }

        val subproject = getSubproject()
        val packageName = getPackageName()
        val packagePath = getPackagePath()

        val interfaceWithDefaultMethodFullName = "$packagePath/$INTERFACE_WITH_DEFAULT_METHOD"
        val classUsingInterfaceWithDefaultMethodFullName =
            "$packagePath/$CLASS_USING_INTERFACE_WITH_DEFAULT_METHOD"
        val standAloneClassFullName = "$packagePath/$STAND_ALONE_CLASS"

        // Java source files
        val javaFile: (classFullName: String) -> File =
            { File("${subproject.mainSrcDir}/$it.java") }
        interfaceWithDefaultMethodJavaFile = javaFile(interfaceWithDefaultMethodFullName)
        classUsingInterfaceWithDefaultMethodJavaFile =
            javaFile(classUsingInterfaceWithDefaultMethodFullName)
        standAloneClassJavaFile = javaFile(standAloneClassFullName)

        FileUtils.mkdirs(interfaceWithDefaultMethodJavaFile.parentFile)
        interfaceWithDefaultMethodJavaFile.writeText(
            """
            package $packageName;

            interface $INTERFACE_WITH_DEFAULT_METHOD {

                default void defaultMethod() {
                    System.out.println("Hello from default method!");
                }

                void nonDefaultMethod();
            }
            """.trimIndent()
        )

        FileUtils.mkdirs(classUsingInterfaceWithDefaultMethodJavaFile.parentFile)
        classUsingInterfaceWithDefaultMethodJavaFile.writeText(
            """
            package $packageName;

            class $CLASS_USING_INTERFACE_WITH_DEFAULT_METHOD implements $INTERFACE_WITH_DEFAULT_METHOD {

                @Override
                public void nonDefaultMethod() {
                    System.out.println("Hello from non-default method!");
                }
            }
            """.trimIndent()
        )

        FileUtils.mkdirs(standAloneClassJavaFile.parentFile)
        standAloneClassJavaFile.writeText(
            """
            package $packageName;

            class $STAND_ALONE_CLASS {
            }
            """.trimIndent()
        )

        // Compiled class files
        val classFile: (classFullName: String) -> File =
            when (scenario) {
                APP, ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> { classFullName ->
                    JAVAC.getOutputDir(subproject.buildDir)
                        .resolve("debug/classes/$classFullName.class")
                }
                JAVA_LIB -> { classFullName ->
                    subproject.buildDir.resolve("classes/java/main/$classFullName.class")
                }
            }
        interfaceWithDefaultMethodClassFile = classFile(interfaceWithDefaultMethodFullName)
        classUsingInterfaceWithDefaultMethodClassFile =
            classFile(classUsingInterfaceWithDefaultMethodFullName)
        standAloneClassClassFile = classFile(standAloneClassFullName)

        // Published class files (from libraries), `null` for app
        val publishedClassFile: (classFullName: String) -> File? =
            when (scenario) {
                APP -> { _ ->
                    null
                }
                ANDROID_LIB -> { classFullName ->
                    if (withMinSdk24Plus) {
                        RUNTIME_LIBRARY_CLASSES_DIR.getOutputDir(subproject.buildDir)
                            .resolve("debug/$classFullName.class")
                    } else {
                        RUNTIME_LIBRARY_CLASSES_JAR.getOutputDir(subproject.buildDir)
                            .resolve("debug/classes.jar")
                    }
                }
                ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> { _ ->
                    if (withMinSdk24Plus) {
                        RUNTIME_LIBRARY_CLASSES_DIR.getOutputDir(subproject.buildDir)
                            .resolve("debug/classes.jar")
                    } else {
                        RUNTIME_LIBRARY_CLASSES_JAR.getOutputDir(subproject.buildDir)
                            .resolve("debug/classes.jar")
                    }
                }
                JAVA_LIB -> { classFullName ->
                    subproject.buildDir.resolve("classes/java/main/$classFullName.class")
                }
            }
        interfaceWithDefaultMethodPublishedClassFile =
            publishedClassFile(interfaceWithDefaultMethodFullName)
        classUsingInterfaceWithDefaultMethodPublishedClassFile =
            publishedClassFile(classUsingInterfaceWithDefaultMethodFullName)
        standAloneClassPublishedClassFile = publishedClassFile(standAloneClassFullName)

        // Directory containing output dex files
        val dexDir = when (scenario) {
            APP -> PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir)
            ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> androidLib.buildDir.resolve(".transforms")
            JAVA_LIB -> javaLib.buildDir.resolve(".transforms")
        }

        incrementalTestHelper = IncrementalTestHelper(
            project = project,
            buildTasks = listOf(":app:mergeProjectDexDebug", ":app:mergeLibDexDebug"),
            filesOrDirsToTrackChanges = setOf(
                interfaceWithDefaultMethodClassFile,
                classUsingInterfaceWithDefaultMethodClassFile,
                standAloneClassClassFile
            ) + listOfNotNull( // These can be null for app
                interfaceWithDefaultMethodPublishedClassFile,
                classUsingInterfaceWithDefaultMethodPublishedClassFile,
                standAloneClassPublishedClassFile
            ) + dexDir
        )
    }

    /**
     * Finds the dex files after a build.
     *
     * We can't locate them before the build because they may reside in a
     * `<project>/build/.transforms/<hash>` directory where `<hash>` is not known in advance.
     */
    private fun findDexFiles() {
        val packagePath = getPackagePath()

        val interfaceWithDefaultMethodFullName = "$packagePath/$INTERFACE_WITH_DEFAULT_METHOD"
        val classUsingInterfaceWithDefaultMethodFullName =
            "$packagePath/$CLASS_USING_INTERFACE_WITH_DEFAULT_METHOD"
        val standAloneClassFullName = "$packagePath/$STAND_ALONE_CLASS"

        val dexFile: (classFullName: String) -> File =
            when (scenario) {
                APP -> { classFullName ->
                    PROJECT_DEX_ARCHIVE.getOutputDir(app.buildDir)
                        .resolve("debug/out/$classFullName.dex")
                }
                ANDROID_LIB -> { classFullName ->
                    if (withMinSdk24Plus) {
                        findDexTransformDir(androidLib).resolve("transformed/debug/$classFullName.dex")
                    } else {
                        findDexTransformDir(androidLib).resolve("transformed/classes/classes.dex")
                    }
                }
                ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> { _ ->
                    findDexTransformDir(androidLib).resolve("transformed/classes/classes.dex")
                }
                JAVA_LIB -> { classFullName ->
                    if (withMinSdk24Plus) {
                        findDexTransformDir(javaLib).resolve("transformed/main/$classFullName.dex")
                    } else {
                        findDexTransformDir(javaLib).resolve("transformed/jetified-javalib/classes.dex")
                    }
                }
            }
        interfaceWithDefaultMethodDexFile = dexFile(interfaceWithDefaultMethodFullName)
        classUsingInterfaceWithDefaultMethodDexFile =
            dexFile(classUsingInterfaceWithDefaultMethodFullName)
        standAloneClassDexFile = dexFile(standAloneClassFullName)
    }

    /**
     * Finds the dex transform directories after a build.
     *
     * We can't locate them before the build because they take the form of
     * `<project>/build/.transforms/<hash>` where `<hash>` is not known in advance.
     *
     * For example, if the given project contains
     * `<project>/build/.transforms/16e674a220d3c8cee4f318266cc44626/transformed/classes/classes.dex`, this
     * method returns `<project>/build/.transforms/16e674a220d3c8cee4f318266cc44626`.
     */
    private fun findDexTransformDir(project: GradleTestProject): File {
        val dexDirs = project.buildDir.resolve(".transforms").listFiles()!!.filter {
            it.isDirectory && it.walk().any { file ->
                file.extension.equals("dex", ignoreCase = true)
            }
        }

        check(dexDirs.isNotEmpty()) { "Can't find dex files in `${project.buildDir.path}`" }
        check(dexDirs.size == 1) {
            "Expected 1 dex directory but found multiple ones: " +
                    dexDirs.joinToString(", ", transform = { it.path })
        }
        return dexDirs[0]
    }

    @Test
    fun `change interface with default method, with a method signature change`() {
        val testHelper = incrementalTestHelper
            .runFullBuild()
            .applyChange {
                TestFileUtils.searchAndReplace(
                    interfaceWithDefaultMethodJavaFile,
                    "default void defaultMethod() {",
                    "default void defaultMethodWithChangedSignature() {"
                )
            }
            .runIncrementalBuild()
        findDexFiles()

        when (scenario) {
            APP -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassDexFile to UNCHANGED
                    )
                )
            }
            ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassPublishedClassFile!! to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassDexFile to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                }
                    )
                )
            }
            JAVA_LIB -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassPublishedClassFile!! to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                }
                    )
                )
            }
        }
    }

    @Test
    fun `change interface with default method, with a method body change`() {
        val testHelper = incrementalTestHelper
            .runFullBuild()
            .applyChange {
                TestFileUtils.searchAndReplace(
                    interfaceWithDefaultMethodJavaFile,
                    "System.out.println(\"Hello from default method!\")",
                    "System.out.println(\"Hello from default method, with changed method body!\")"
                )
            }
            .runIncrementalBuild()
        findDexFiles()

        when (scenario) {
            APP -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS
                                },
                        standAloneClassDexFile to UNCHANGED
                    )
                )
            }
            ANDROID_LIB, ANDROID_LIB_WITH_POST_JAVAC_CLASSES -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassPublishedClassFile!! to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassDexFile to
                                if (scenario == ANDROID_LIB && withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                }
                    )
                )
            }
            JAVA_LIB -> {
                testHelper.assertFileChanges(
                    mapOf(
                        // Compiled class files
                        interfaceWithDefaultMethodClassFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodClassFile to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassClassFile to UNCHANGED,
                        // Published class files
                        interfaceWithDefaultMethodPublishedClassFile!! to CHANGED,
                        classUsingInterfaceWithDefaultMethodPublishedClassFile!! to CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,
                        standAloneClassPublishedClassFile!! to UNCHANGED,
                        // Dex files
                        interfaceWithDefaultMethodDexFile to CHANGED,
                        classUsingInterfaceWithDefaultMethodDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                },
                        standAloneClassDexFile to
                                if (withMinSdk24Plus) {
                                    UNCHANGED
                                } else {
                                    CHANGED
                                }
                    )
                )
            }
        }
    }

    enum class Scenario {

        /** Apply a change in the app subproject. */
        APP,

        /** Apply a change in an Android library subproject. */
        ANDROID_LIB,

        /**
         * Apply a change in an Android library, where the library has additional post-Javac
         * classes.
         */
        ANDROID_LIB_WITH_POST_JAVAC_CLASSES,

        /** Apply a change in a Java library subproject. */
        JAVA_LIB
    }
}
