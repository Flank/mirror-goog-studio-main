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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.APP
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.COM_EXAMPLE_LIB
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.COM_EXAMPLE_MYAPPLICATION
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder.Companion.LIB
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.ChangeType
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.DEX
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Tests that dex merging is incremental. */
class IncrementalDexMergingTest {

    @get:Rule
    val project = EmptyActivityProjectBuilder()
        .also { it.minSdkVersion = 21 }
        .addAndroidLibrary(addImplementationDependencyFromApp = true)
        .build()

    private lateinit var app: GradleTestProject
    private lateinit var lib: GradleTestProject

    // Java classes
    private lateinit var changedClassInApp: String
    private lateinit var unchangedClassesInApp: List<String>
    private lateinit var changedClassInLib: String
    private lateinit var unchangedClassesInLib: List<String>

    // Java files
    private lateinit var changedJavaFileInApp: File
    private lateinit var unchangedJavaFilesInApp: List<File>
    private lateinit var changedJavaFileInLib: File
    private lateinit var unchangedJavaFilesInLib: List<File>

    // Merged dex directories
    private lateinit var mergedDexDirForApp: File
    private lateinit var mergedDexDirForLib: File
    private lateinit var mergedDexDirForExternalLibraries: File

    private lateinit var incrementalTestHelper: IncrementalTestHelper

    @Before
    fun setUp() {
        app = project.getSubproject(APP)
        lib = project.getSubproject(LIB)

        // Add Java files
        addJavaFiles(
            app,
            COM_EXAMPLE_MYAPPLICATION
        ) { changedClass, unchangedClasses, changedJavaFile, unchangedJavaFiles ->
            changedClassInApp = changedClass
            unchangedClassesInApp = unchangedClasses
            changedJavaFileInApp = changedJavaFile
            unchangedJavaFilesInApp = unchangedJavaFiles
        }
        addJavaFiles(
            lib,
            COM_EXAMPLE_LIB
        ) { changedClass, unchangedClasses, changedJavaFile, unchangedJavaFiles ->
            changedClassInLib = changedClass
            unchangedClassesInLib = unchangedClasses
            changedJavaFileInLib = changedJavaFile
            unchangedJavaFilesInLib = unchangedJavaFiles
        }

        // Get merged dex directories
        val mergedDexDir = DEX.getOutputDir(app.buildDir).resolve("debug")
        mergedDexDirForApp = mergedDexDir.resolve("mergeProjectDexDebug")
        mergedDexDirForLib = mergedDexDir.resolve("mergeLibDexDebug")
        mergedDexDirForExternalLibraries = mergedDexDir.resolve("mergeExtDexDebug")

        incrementalTestHelper = IncrementalTestHelper(
            project = project,
            buildTask = ":app:assembleDebug",
            filesOrDirsToTrackChanges = setOf(
                mergedDexDirForApp,
                mergedDexDirForLib,
                mergedDexDirForExternalLibraries
            )
        )
    }

    private fun addJavaFiles(
        subproject: GradleTestProject,
        packageName: String,
        collectJavaFiles: (changedClass: String, unchangedClasses: List<String>, changedJavaFile: File, unchangedJavaFiles: List<File>) -> Unit
    ) {
        val classFullName: (className: String) -> String =
            { "${packageName.replace('.', '/')}/$it" }
        val javaFile: (classFullName: String) -> File =
            { File("${subproject.mainSrcDir}/$it.java") }

        val changedClassName = "ChangedClass"
        val changedClassFullName = classFullName(changedClassName)
        val changedJavaFile = javaFile(changedClassFullName)
        FileUtils.mkdirs(changedJavaFile.parentFile)
        changedJavaFile.writeText(
            """
            package $packageName;

            public class $changedClassName {

                public void someMethod() {
                    System.out.println("Change me!");
                }
            }
            """.trimIndent()
        )

        val unchangedClassFullNames = mutableListOf<String>()
        val unchangedJavaFiles = mutableListOf<File>()
        for (i in 0 until NUMBER_OF_UNCHANGED_CLASSES_PER_SUBPROJECT) {
            val unchangedClassName = "UnchangedClass$i"
            val unchangedClassFullName = classFullName(unchangedClassName)
            val unchangedJavaFile = javaFile(unchangedClassFullName)
            FileUtils.mkdirs(unchangedJavaFile.parentFile)
            unchangedJavaFile.writeText(
                """
                package $packageName;

                public class $unchangedClassName {

                    public void someMethod() {
                        System.out.println("Don't change me!");
                    }
                }
                """.trimIndent()
            )
            unchangedClassFullNames.add(unchangedClassFullName)
            unchangedJavaFiles.add(unchangedJavaFile)
        }

        collectJavaFiles(
            changedClassFullName,
            unchangedClassFullNames,
            changedJavaFile,
            unchangedJavaFiles
        )
    }

    @Test
    fun `change Java file`() {
        val testHelper = incrementalTestHelper
            .runFullBuild()
            .applyChange {
                listOf(changedJavaFileInApp, changedJavaFileInLib).forEach {
                    searchAndReplace(it, "Change me!", "I'm changed!")
                }
            }
            .runIncrementalBuild()

        // Get merged dex files
        val mergedDexFilesForApp = FileUtils.getAllFiles(mergedDexDirForApp)
        val mergedDexFilesForLib = FileUtils.getAllFiles(mergedDexDirForLib)
        val mergedDexFilesForExternalLibraries =
            FileUtils.getAllFiles(mergedDexDirForExternalLibraries)

        // TODO(132615300) Update assertions once dex merging is made incremental
        assertThat(mergedDexFilesForApp.size()).isEqualTo(1)
        assertThat(mergedDexFilesForLib.size()).isEqualTo(1)
        assertThat(mergedDexFilesForExternalLibraries.size()).isEqualTo(1)
        testHelper.assertFileChanges(
            mergedDexFilesForApp.toMap { ChangeType.CHANGED }
                    + mergedDexFilesForLib.toMap { ChangeType.CHANGED }
                    + mergedDexFilesForExternalLibraries.toMap { ChangeType.UNCHANGED }
        )

        app.getApk(DEBUG).use { apk ->
            (unchangedClassesInApp + changedClassInApp + changedClassInLib + unchangedClassesInLib).forEach {
                assertThat(apk).containsClass("L$it;")
            }
        }
    }
}

/** Number of classes that we add to a subproject and will not change between builds. */
private const val NUMBER_OF_UNCHANGED_CLASSES_PER_SUBPROJECT = 10
