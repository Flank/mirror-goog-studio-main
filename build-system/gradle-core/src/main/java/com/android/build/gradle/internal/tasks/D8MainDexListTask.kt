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

package com.android.build.gradle.internal.tasks

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.PROJECT
import com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.Scope.TESTED_CODE
import com.android.build.gradle.internal.InternalScope.FEATURES
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.multidex.D8MainDexList
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Calculate the main dex list using D8.
 */
@CacheableTask
abstract class D8MainDexListTask @Inject constructor(executor: WorkerExecutor) :
    NonIncrementalTask() {

    @get:Input
    abstract var errorFormat: SyncOptions.ErrorFormatMode
        protected set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract var aaptGeneratedRules: FileCollection
        protected set

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract var userMultidexProguardRules: File?
        protected set

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract var userMultidexKeepFile: File?
        protected set

    @get:Classpath
    abstract var bootClasspath: FileCollection
        protected set

    @get:Classpath
    abstract var inputClasses: FileCollection
        protected set

    @get:Classpath
    abstract var libraryClasses: FileCollection
        protected set

    @get:OutputFile
    abstract var output: Provider<RegularFile>
        protected set

    private val workers = Workers.preferWorkers(project.name, path, executor)

    override fun doTaskAction() {

        val programClasses = inputClasses.files
        val libraryFilesNotInInputs =
            libraryClasses.files.filter { !programClasses.contains(it) } + bootClasspath.files

        workers.use {
            it.submit(
                MainDexListRunnable::class.java,
                MainDexListRunnable.Params(
                    listOfNotNull(aaptGeneratedRules.singleFile, userMultidexProguardRules),
                    programClasses,
                    libraryFilesNotInInputs,
                    userMultidexKeepFile,
                    output.get().asFile,
                    errorFormat
                )
            )
        }
    }

    class MainDexListRunnable @Inject constructor(val params: Params) : Runnable {

        class Params(
            val proguardRules: Collection<File>,
            val programFiles: Collection<File>,
            val libraryFiles: Collection<File>,
            val userMultidexKeepFile: File?,
            val output: File,
            val errorFormat: SyncOptions.ErrorFormatMode
        ) : Serializable

        override fun run() {
            val logger = Logging.getLogger(D8MainDexListTask::class.java)
            logger.debug("Generating the main dex list using D8.")
            logger.debug("Program files: %s", params.programFiles.joinToString())
            logger.debug("Library files: %s", params.libraryFiles.joinToString())
            logger.debug("Proguard rule files: %s", params.proguardRules.joinToString())

            val mainDexClasses = mutableSetOf<String>()

            mainDexClasses.addAll(
                D8MainDexList.generate(
                    getPlatformRules(),
                    params.proguardRules.map { it.toPath() },
                    params.programFiles.map { it.toPath() },
                    params.libraryFiles.map { it.toPath() },
                    MessageReceiverImpl(params.errorFormat, logger)
                )
            )

            params.userMultidexKeepFile?.let {
                mainDexClasses.addAll(it.readLines())
            }

            params.output.writeText(mainDexClasses.joinToString(separator = System.lineSeparator()))
        }
    }

    class CreationAction(
        scope: VariantScope,
        private val includeDynamicFeatures: Boolean
    ) : VariantTaskCreationAction<D8MainDexListTask>(scope) {

        private val inputClasses: FileCollection
        private val libraryClasses: FileCollection

        init {
            val inputScopes: Set<QualifiedContent.ScopeType> = setOf(
                PROJECT,
                SUB_PROJECTS,
                EXTERNAL_LIBRARIES
            ) + (if (includeDynamicFeatures) setOf(FEATURES) else emptySet())

            val libraryScopes = setOf(PROVIDED_ONLY, TESTED_CODE)
            val allScopes = inputScopes + libraryScopes

            inputClasses = scope.transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(
                        QualifiedContent.DefaultContentType.CLASSES
                    ) && allScopes.intersect(scopes).size == scopes.size
                            && inputScopes.intersect(scopes).isNotEmpty()
                }
            libraryClasses = scope.transformManager
                .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                    contentTypes.contains(
                        QualifiedContent.DefaultContentType.CLASSES
                    ) && allScopes.intersect(scopes).size == scopes.size
                            && libraryScopes.intersect(scopes).isNotEmpty()
                }
        }

        private lateinit var output: Provider<RegularFile>

        override val name: String =
            scope.getTaskName(if (includeDynamicFeatures) "bundleMultiDexList" else "multiDexList")
        override val type: Class<D8MainDexListTask> = D8MainDexListTask::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            val outputType =
                if (includeDynamicFeatures) {
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE
                } else {
                    InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST
                }
            output = variantScope.artifacts.createArtifactFile(
                outputType, BuildArtifactsHolder.OperationType.INITIAL, taskName, "mainDexList.txt"
            )
        }

        override fun configure(task: D8MainDexListTask) {
            super.configure(task)
            task.output = output

            task.aaptGeneratedRules =
                variantScope.artifacts.getFinalArtifactFiles(
                    InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES
                ).get()
            task.userMultidexProguardRules = variantScope.variantConfiguration.multiDexKeepProguard
            task.userMultidexKeepFile = variantScope.variantConfiguration.multiDexKeepFile

            task.inputClasses = inputClasses
            task.libraryClasses = libraryClasses

            task.bootClasspath = variantScope.bootClasspath
            task.errorFormat =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
        }
    }
}

internal fun getPlatformRules(): List<String> = listOf(
    "-keep public class * extends android.app.Instrumentation {\n"
            + "  <init>(); \n"
            + "  void onCreate(...);\n"
            + "  android.app.Application newApplication(...);\n"
            + "  void callApplicationOnCreate(android.app.Application);\n"
            + "  Z onException(java.lang.Object, java.lang.Throwable);\n"
            + "}",
    "-keep public class * extends android.app.Application { "
            + "  <init>();\n"
            + "  void attachBaseContext(android.content.Context);\n"
            + "}",
    "-keep public class * extends android.app.backup.BackupAgent { <init>(); }",
    "-keep public class * implements java.lang.annotation.Annotation { *;}",
    "-keep public class * extends android.test.InstrumentationTestCase { <init>(); }"
)