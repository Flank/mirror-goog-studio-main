/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.base.Suppliers
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.util.function.Supplier

/**
 * Task to write an android manifest for the res.apk static library
 */
@CacheableTask
abstract class StaticLibraryManifestTask : NonIncrementalTask() {

    @get:Internal lateinit var packageNameSupplier: Supplier<String> private set
    @get:Input val packageName get() = packageNameSupplier.get()
    @get:OutputFile abstract val manifestFile: RegularFileProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                StaticLibraryManifestRunnable::class.java,
                StaticLibraryManifestRequest(manifestFile.get().asFile, packageName)
            )
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<StaticLibraryManifestTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "StaticLibraryManifest")
        override val type: Class<StaticLibraryManifestTask>
            get() = StaticLibraryManifestTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out StaticLibraryManifestTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.STATIC_LIBRARY_MANIFEST,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                StaticLibraryManifestTask::manifestFile,
                SdkConstants.ANDROID_MANIFEST_XML
            )
        }

        override fun configure(task: StaticLibraryManifestTask) {
            super.configure(task)
            task.packageNameSupplier =
                    Suppliers.memoize(variantScope.variantConfiguration::getOriginalApplicationId)
        }
    }
}