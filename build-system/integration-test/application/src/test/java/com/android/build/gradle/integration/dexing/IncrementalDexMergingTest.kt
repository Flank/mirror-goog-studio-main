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
import com.android.build.gradle.integration.common.utils.ChangeType.CHANGED
import com.android.build.gradle.integration.common.utils.ChangeType.UNCHANGED
import com.android.build.gradle.integration.common.utils.IncrementalTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils.searchAndReplace
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.DEX
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.internal.tasks.DexMergingTaskDelegate
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.apk.Dex
import com.android.testutils.apk.Zip
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
            .also {
                // Incremental dexing transform is enabled for SDK 24+, set this so we can test that
                // case.
                it.minSdkVersion = 24
            }
            .addAndroidLibrary(addImplementationDependencyFromApp = true)
            .build()

    private lateinit var app: GradleTestProject
    private lateinit var lib: GradleTestProject

    // Java classes
    private lateinit var classesInApp: List<String>
    private lateinit var classesInLib: List<String>
    private lateinit var changedClassInApp: String
    private lateinit var changedClassInLib: String

    // Java files
    private lateinit var changedJavaFileInApp: File
    private lateinit var changedJavaFileInLib: File

    // Merged dex directories
    private lateinit var mergedDexDirForApp: File
    private lateinit var mergedDexDirForLib: File
    private lateinit var mergedDexDirForExtLibs: File

    private lateinit var incrementalTestHelper: IncrementalTestHelper

    @Before
    fun setUp() {
        app = project.getSubproject(APP)
        lib = project.getSubproject(LIB)

        // Add Java files
        addJavaFiles(
                app,
                COM_EXAMPLE_MYAPPLICATION
        ) { classes, changedClass, changedJavaFile ->
            classesInApp = classes
            changedClassInApp = changedClass
            changedJavaFileInApp = changedJavaFile
        }
        addJavaFiles(
                lib,
                COM_EXAMPLE_LIB
        ) { classes, changedClass, changedJavaFile ->
            classesInLib = classes
            changedClassInLib = changedClass
            changedJavaFileInLib = changedJavaFile
        }

        // Get merged dex directories
        val mergedDexDir = DEX.getOutputDir(app.buildDir).resolve("debug")
        mergedDexDirForApp = mergedDexDir.resolve("mergeProjectDexDebug")
        mergedDexDirForLib = mergedDexDir.resolve("mergeLibDexDebug")
        mergedDexDirForExtLibs = mergedDexDir.resolve("mergeExtDexDebug")

        incrementalTestHelper = IncrementalTestHelper(
                project = project,
                buildTask = ":app:assembleDebug",
                filesOrDirsToTrackChanges = setOf(
                        mergedDexDirForApp,
                        mergedDexDirForLib,
                        mergedDexDirForExtLibs
                )
        ).updateExecutor {
            it.with(IntegerOption.DEXING_NUMBER_OF_BUCKETS, NUMBER_OF_BUCKETS)
        }
    }

    private fun addJavaFiles(
            subproject: GradleTestProject,
            subprojectPackageName: String,
            collectClasses: (classes: List<String>, changedClass: String, changedJavaFile: File) -> Unit
    ) {
        val getClassFullName: (packageName: String, className: String) -> String =
                { packageName, className -> "${packageName.replace('.', '/')}/$className" }
        val getJavaFile: (classFullName: String) -> File =
                { File("${subproject.mainSrcDir}/$it.java") }

        val classes = mutableListOf<String>()
        var changedClass: String? = null
        var changedJavaFile: File? = null

        for (i in 0 until NUMBER_OF_PACKAGES_PER_SUBPROJECT) {
            val packageName = "$subprojectPackageName.package$i"
            for (j in 0 until NUMBER_OF_CLASSES_PER_PACKAGE) {
                val className = "Class$j"
                val classFullName = getClassFullName(packageName, className)
                val javaFile = getJavaFile(classFullName)

                FileUtils.mkdirs(javaFile.parentFile)
                val text = if (i == 0 && j == 0) {
                    changedClass = classFullName
                    changedJavaFile = javaFile
                    "Change me!"
                } else {
                    "Don't change me!"
                }
                javaFile.writeText(
                        """
                        package $packageName;

                        public class $className {

                            public void someMethod() {
                                System.out.println("$text");
                            }
                        }
                        """.trimIndent()
                )
                classes.add(classFullName)
            }
        }

        collectClasses(classes, changedClass!!, changedJavaFile!!)
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

        val checkSubproject = { changedClass: String, mergedDexDir: File ->
            // Check that dex files are put into buckets
            assertThat(mergedDexDir.listFiles()).hasLength(NUMBER_OF_BUCKETS)

            // Check that only the impacted bucket(s) are re-merged
            val bucketWithChangedClass =
                    DexMergingTaskDelegate.getBucketNumber("$changedClass.dex", NUMBER_OF_BUCKETS)
            val dexFiles = FileUtils.getAllFiles(mergedDexDir)
            val dexFileWithChangedClass =
                    mergedDexDir.resolve(bucketWithChangedClass.toString()).resolve("classes.dex")
            val dexFilesWithoutChangedClass = dexFiles.filter { it != dexFileWithChangedClass }
            testHelper.assertFileChanges(
                    mapOf(dexFileWithChangedClass to CHANGED) +
                            dexFilesWithoutChangedClass.map { it to UNCHANGED }
            )

            // Also check that classes of the same package are put in the same bucket/merged dex
            // file (except for R classes, see the comments in `getBucketNumber` of DexMergingTask)
            val rClassesPath =
                    COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR.getOutputDir(app.buildDir)
                            .resolve("debug/R.jar")
            val rClasses = Zip(rClassesPath).entries
                    .map {
                        // Normalize to the form of Lcom/example/package/ExampleClass;
                        "L${it.toString().substring(1).substringBefore(".class")};"
                    }
            val packageToDexFileMap = mutableMapOf<String, File>()
            for (dexFile in dexFiles) {
                Dex(dexFile).classes.keys.forEach { fullClassName ->
                    // fullClassName is in the form of Lcom/example/package/ExampleClass;
                    if (fullClassName in rClasses) {
                        return@forEach
                    }
                    val packageName = fullClassName.substringAfter('L').substringBeforeLast('/')
                    val previousDexFile = packageToDexFileMap.put(packageName, dexFile)
                    assert(previousDexFile == null || previousDexFile == dexFile) {
                        "Package $packageName is found in both $previousDexFile and $dexFile"
                    }
                }
            }
        }

        // Check merged dex files from app and lib
        checkSubproject(changedClassInApp, mergedDexDirForApp)
        checkSubproject(changedClassInLib, mergedDexDirForLib)

        // Check merged dex files from external libraries
        assertThat(mergedDexDirForExtLibs.listFiles()!!.size).isEqualTo(1)
        testHelper.assertFileChanges(
                mapOf(FileUtils.getAllFiles(mergedDexDirForExtLibs).single() to UNCHANGED)
        )

        // Check contents of the APK
        app.getApk(DEBUG).use { apk ->
            (classesInApp + classesInLib).forEach {
                assertThat(apk).containsClass("L$it;")
            }
        }
    }
}

/** Number of packages added to a subproject. */
private const val NUMBER_OF_PACKAGES_PER_SUBPROJECT = 10

/** Number of classes added to a package. */
private const val NUMBER_OF_CLASSES_PER_PACKAGE = 2

/** Number of buckets used by DexMergingTask. */
private const val NUMBER_OF_BUCKETS = 4
