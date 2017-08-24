/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.scope.AndroidTaskRegistry
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.TaskOutputHolder
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.core.VariantType
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import java.io.File

/**
 * Responsible for the creation of tasks to build namespaced resources.
 */
class NamespacedResourcesTaskManager(
        val globalScope: GlobalScope,
        val androidTasks: AndroidTaskRegistry,
        val tasks: TaskFactory,
        val variantScope: VariantScope) {

    /**
     * Creates the tasks for dealing with resources in a namespaced way.
     *
     * The current implementation:
     *
     *  1. Links the app as a static library. This provides a *non-final* R-class, and means that
     *  the distinction between app and library is reduced. We can revisit that in the future if
     *  final ids in apps are a vital feature.
     *  2. Links the app and its dependency to produce the final APK. This re-uses the same
     *  [ProcessAndroidResources] task, as it needs to be split aware.
     *

     * TODO: Test support, Synthesize non-namespaced output.
     */
    fun createNamespacedResourceTasks(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val aaptGeneration = AaptGeneration.fromProjectOptions(globalScope.projectOptions)
        Preconditions.checkState(
                aaptGeneration == AaptGeneration.AAPT_V2_JNI || aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_MODE,
                "Resource Namespacing can only be used with aapt2")
        // Compile
        createCompileResourcesTask()
        createLinkResourcesTask()
        createCompileCompileOnlyRClassTask()
        createJarCompileOnlyRClassClassesTask()

        if (variantScope.variantData.type == VariantType.LIBRARY || variantScope.testedVariantData?.type == VariantType.LIBRARY) {
            createNamespacedLibraryProcessResourcesTask(
                    resPackageOutputFolder = resPackageOutputFolder,
                    packageOutputType = packageOutputType)
        } else {
            createNamespacedAppProcessTask(
                    resPackageOutputFolder = resPackageOutputFolder,
                    packageOutputType = packageOutputType,
                    baseName = baseName,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules = useAaptToGenerateLegacyMultidexMainDexProguardRules)
        }
        createCompileRuntimeRClassTask()
    }

    private fun createCompileCompileOnlyRClassTask() {
        val compileOnlyRClassClassesDir = File(
                variantScope.globalScope.intermediatesDir,
                "res-r/" + variantScope.variantConfiguration.dirName)

        val task = androidTasks.create(
                tasks,
                CompileRClassTask.ConfigAction(
                        variantScope.getTaskName("compile", "RClass"),
                        variantScope.getOutput(TaskOutputType.COMPILE_ONLY_R_CLASS_SOURCES),
                        compileOnlyRClassClassesDir
                ))
        variantScope.addTaskOutput(
                TaskOutputType.COMPILE_ONLY_R_CLASS_CLASSES,
                compileOnlyRClassClassesDir,
                task.name)
    }

    private fun createJarCompileOnlyRClassClassesTask() {
        val rClassJarFile = File(
                variantScope.globalScope.intermediatesDir,
                "res-rJar/" + variantScope.variantConfiguration.dirName + "/R.jar")
        val task =
                androidTasks.create(
                        tasks,
                        JarRClassTask.ConfigAction(
                                variantScope.getTaskName("jar", "RClass"),
                                variantScope.getOutput(TaskOutputType.COMPILE_ONLY_R_CLASS_CLASSES),
                                rClassJarFile))
        variantScope.addTaskOutput(
                TaskOutputType.COMPILE_ONLY_R_CLASS_JAR,
                rClassJarFile,
                task.name)
    }

    private fun createCompileRuntimeRClassTask() {
        val rClassCompiledOutputDir = File(
                variantScope.globalScope.intermediatesDir,
                "res-final-r-classes/" + variantScope.variantConfiguration.dirName)
        val task = androidTasks.create(
                tasks,
                CompileRClassTask.ConfigAction(
                        variantScope.getTaskName("compile", "FinalRClass"),
                        variantScope.getOutput(TaskOutputType.RUNTIME_R_CLASS_SOURCES),
                        rClassCompiledOutputDir
                ))
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_CLASSES,
                rClassCompiledOutputDir,
                task.name)
    }

    private fun createNamespacedAppProcessTask(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val runtimeRClassSources = File(globalScope.generatedDir,
                "source/final-r/" + variantScope.variantConfiguration.dirName)
        val process = androidTasks.create(
                tasks,
                ProcessAndroidResources.NamespacedConfigAction(
                        variantScope,
                        runtimeRClassSources,
                        resPackageOutputFolder,
                        variantScope.variantData.type == VariantType.LIBRARY,
                        useAaptToGenerateLegacyMultidexMainDexProguardRules,
                        baseName))
        variantScope.addTaskOutput(
                TaskOutputType.PROCESSED_RES,
                resPackageOutputFolder,
                process.name)
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_SOURCES,
                runtimeRClassSources,
                process.name)
        if (packageOutputType != null) {
            variantScope.addTaskOutput(
                    packageOutputType,
                    variantScope.processResourcePackageOutputDirectory,
                    process.name)
        }
    }

    private fun createNamespacedLibraryProcessResourcesTask(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?) {
        val runtimeRClassSources = File(globalScope.generatedDir,
                "source/final-r/" + variantScope.variantConfiguration.dirName)
        val process = androidTasks.create(
                tasks,
                ProcessAndroidAppResourcesTask.ConfigAction(
                        variantScope,
                        runtimeRClassSources,
                        File(resPackageOutputFolder, "res.apk"),
                        variantScope.variantData.type == VariantType.LIBRARY))
        variantScope.addTaskOutput(
                TaskOutputType.PROCESSED_RES,
                resPackageOutputFolder,
                process.name)
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_SOURCES,
                runtimeRClassSources,
                process.name)
        if (packageOutputType != null) {
            variantScope.addTaskOutput(
                    packageOutputType,
                    variantScope.processResourcePackageOutputDirectory,
                    process.name)
        }
    }

    private fun createCompileResourcesTask() {
        val compiled =
                FileUtils.join(
                        variantScope.globalScope.intermediatesDir,
                        SdkConstants.FD_RES,
                        SdkConstants.FD_COMPILED,
                        variantScope.variantConfiguration.dirName)
        val compile = androidTasks.create(
                tasks,
                MergeResources.ConfigAction(
                        variantScope,
                        "compile",
                        compiled,
                        null,
                        false,
                        true,
                        false))
        compile.dependsOn(tasks, variantScope.resourceGenTask)
        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.RES_COMPILED_FLAT_FILES, compiled, compile.name)
    }

    private fun createLinkResourcesTask() {
        val resourceStaticLibrary =
                FileUtils.join(globalScope.intermediatesDir,
                        "res-linked",
                        variantScope.variantConfiguration.dirName,
                        "res.apk")
        val compileOnlyRClassSourceDir = File(globalScope.generatedDir,
                "source/r/" + variantScope.variantConfiguration.dirName)
        val link = androidTasks.create(
                tasks,
                LinkLibraryAndroidResourcesTask.ConfigAction(
                        variantScope, compileOnlyRClassSourceDir, resourceStaticLibrary))
        variantScope.addTaskOutput(
                TaskOutputType.RES_STATIC_LIBRARY,
                resourceStaticLibrary,
                link.name)
        variantScope.addTaskOutput(
                TaskOutputType.COMPILE_ONLY_R_CLASS_SOURCES, compileOnlyRClassSourceDir, link.name)
    }
}