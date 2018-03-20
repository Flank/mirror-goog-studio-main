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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.base.Suppliers
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to write an android manifest for the res.apk static library
 */
@CacheableTask
open class StaticLibraryManifestTask @Inject constructor(
        private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @get:Internal lateinit var packageNameSupplier: Supplier<String> private set
    @get:Input val packageName get() = packageNameSupplier.get()
    @get:OutputFile lateinit var manifestFile: File private set

    @TaskAction
    fun createManifest() {
        workerExecutor.submit(StaticLibraryManifestRunnable::class.java) {
            it.isolationMode = IsolationMode.NONE
            it.setParams(
                    StaticLibraryManifestRequest(
                            manifestFile = manifestFile,
                            packageName = packageName))
        }
    }

    class ConfigAction(
                private val scope: VariantScope) : TaskConfigAction<StaticLibraryManifestTask> {
        override fun getName() = scope.getTaskName("create", "StaticLibraryManifest")
        override fun getType() = StaticLibraryManifestTask::class.java
        override fun execute(task: StaticLibraryManifestTask) {
            task.manifestFile = scope.artifacts.appendArtifact(InternalArtifactType.STATIC_LIBRARY_MANIFEST,
                task,
                SdkConstants.ANDROID_MANIFEST_XML)
            task.packageNameSupplier =
                    Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
        }
    }
}