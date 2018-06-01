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

import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.google.common.base.Preconditions

/**
 * Responsible for the creation of tasks to build namespaced resources.
 */
class NamespacedResourcesTaskManager(
        private val globalScope: GlobalScope,
        private val taskFactory: TaskFactory,
        private val variantScope: VariantScope) {

    /**
     * Creates the tasks for dealing with resources in a namespaced way.
     *
     * The current implementation:
     *
     *  1. Links the app as a static library. This provides a *non-final* R-class, and means that
     *  the distinction between app and library is reduced. We can revisit that in the future if
     *  final ids in apps are a vital feature.
     *  2. Links the app and its dependency to produce the final APK. This re-uses the same
     *  [LinkApplicationAndroidResourcesTask] task, as it needs to be split aware.
     *  3. If rewriting non-namespaced dependencies is enabled, the [AutoNamespaceDependenciesTask]
     *  will rewrite classes.jar files from these libraries to be fully namespaced.
     *
     * TODO: Test support, Synthesize non-namespaced output.
     */
    fun createNamespacedResourceTasks(
            packageOutputType: InternalArtifactType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val aaptGeneration = AaptGeneration.fromProjectOptions(globalScope.projectOptions)
        Preconditions.checkState(aaptGeneration != AaptGeneration.AAPT_V1,
                "Resource Namespacing can only be used with aapt2")

        // Process dependencies making sure everything we consume will be fully namespaced.
        if (globalScope.projectOptions.get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
            // TODO: also rewrite the resources
            taskFactory.create(AutoNamespaceDependenciesTask.ConfigAction(variantScope))
        }

        // Compile
        createCompileResourcesTask()
        taskFactory.create(StaticLibraryManifestTask.ConfigAction(variantScope))
        taskFactory.create(LinkLibraryAndroidResourcesTask.ConfigAction(variantScope))
        // TODO: also generate a private R.jar holding private resources.
        taskFactory.create(GenerateNamespacedLibraryRFilesTask.ConfigAction(variantScope))
        if (variantScope.type.isTestComponent) {
            if (variantScope.testedVariantData!!.type.isAar) {
                createNamespacedLibraryTestProcessResourcesTask(
                    packageOutputType = packageOutputType
                )
            } else {
                createNamespacedAppProcessTask(
                    packageOutputType = packageOutputType,
                    baseName = baseName,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules = false
                )
            }
        } else if (variantScope.type.isApk) {
            createNamespacedAppProcessTask(
                packageOutputType = packageOutputType,
                baseName = baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules = useAaptToGenerateLegacyMultidexMainDexProguardRules
            )
        }
        taskFactory.create(CompileRClassTask.ConfigAction(variantScope))
    }

    private fun createNamespacedAppProcessTask(
            packageOutputType: InternalArtifactType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
       taskFactory.create(LinkApplicationAndroidResourcesTask.NamespacedConfigAction(
           variantScope,
           useAaptToGenerateLegacyMultidexMainDexProguardRules,
           baseName))
        if (packageOutputType != null) {
            variantScope.artifacts.appendArtifact(
                packageOutputType,
                variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES))
        }
    }

    private fun createNamespacedLibraryTestProcessResourcesTask(
            packageOutputType: InternalArtifactType?) {
        taskFactory.create(ProcessAndroidAppResourcesTask.ConfigAction(variantScope))
        if (packageOutputType != null) {
            variantScope.artifacts.appendArtifact(
                packageOutputType,
                variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES))
        }
    }

    private fun createCompileResourcesTask() {
        for((sourceSetName, artifacts) in variantScope.variantData.androidResources) {
            val name = "compile${sourceSetName.capitalize()}" +
                    "ResourcesFor${variantScope.fullVariantName.capitalize()}"
            // TODO : figure out when we need explicit task dependency and potentially remove it.
            taskFactory.create(CompileSourceSetResources.ConfigAction(
                    name = name,
                    inputDirectories = artifacts,
                    variantScope = variantScope))
                .dependsOn(variantScope.resourceGenTask)
        }
    }
}
