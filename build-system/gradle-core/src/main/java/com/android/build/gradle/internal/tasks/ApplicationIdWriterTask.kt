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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.apache.commons.io.FileUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 * Task responsible for publishing the application Id.
 *
 * This task is currently used to publish the output as a text resource for others to consume.
 */
abstract class ApplicationIdWriterTask : NonIncrementalTask() {

    private var applicationIdSupplier: () -> String? = { null }

    @get:Input
    @get:Optional
    val applicationId get() = applicationIdSupplier()

    @get:InputFiles
    @get:Optional
    var appMetadata: FileCollection? = null
        private set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        val resolvedApplicationId = appMetadata?.let {
            ModuleMetadata.load(it.singleFile).applicationId
        } ?: applicationId

        if (resolvedApplicationId != null) {
            FileUtils.write(outputFile.get().asFile, resolvedApplicationId)
        } else {
            logger.error("ApplicationId could not be resolved for $variantName")
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ApplicationIdWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("write", "ApplicationId")
        override val type: Class<ApplicationIdWriterTask>
            get() = ApplicationIdWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ApplicationIdWriterTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(InternalArtifactType.METADATA_APPLICATION_ID,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                ApplicationIdWriterTask::outputFile,
                "application-id.txt")
        }

        override fun configure(task: ApplicationIdWriterTask) {
            super.configure(task)

            if (variantScope.type.isDynamicFeature) {
                // If this is a dynamic feature, we read the value published by the base
                // module and write it down.
                // This is only done so that BaseVariant.getApplicationIdTextResource() can be
                // a bit dynamic.
                // TODO replace this with Property<String> which can be fed from the published artifact directly.
                // b/141650037
                task.appMetadata = variantScope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, PROJECT, BASE_MODULE_METADATA
                )
            } else {
                task.applicationIdSupplier = { variantScope.variantConfiguration.applicationId }
            }
        }
    }
}
