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
package com.android.build.gradle.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.cxx.gradle.generator.CxxMetadataGenerator
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.errors.DefaultIssueReporter
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Task wrapper around ExternalNativeJsonGenerator.  */
abstract class ExternalNativeBuildJsonTask @Inject constructor(private val ops: ExecOperations) :
    UnsafeOutputsTask("Generate json model is always run.") {
    private var generator: Provider<CxxMetadataGenerator>? = null

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val renderscriptSources: DirectoryProperty

    override fun doTaskAction() {
        IssueReporterLoggingEnvironment(DefaultIssueReporter(LoggerWrapper(logger))).use {
            for (future in generator!!.get().getMetadataGenerators(ops, false, null)) {
                future.call()
            }
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        private val generator: Provider<CxxMetadataGenerator>
    ) : VariantTaskCreationAction<ExternalNativeBuildJsonTask, ComponentPropertiesImpl>(componentProperties) {
        override val name
            get() = computeTaskName("generateJsonModel")

        override val type
            get() = ExternalNativeBuildJsonTask::class.java

        override fun configure(task: ExternalNativeBuildJsonTask) {
            super.configure(task)
            task.generator = generator
            val variantDslInfo = creationConfig.variantDslInfo
            if (variantDslInfo.renderscriptNdkModeEnabled) {
                creationConfig
                    .artifacts
                    .setTaskInputToFinalProduct(
                        InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR,
                        task.renderscriptSources
                    )
            }
        }
    }
}