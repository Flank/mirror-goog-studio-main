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

import com.android.annotations.VisibleForTesting
import com.android.build.gradle.internal.dsl.CoreSigningConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.AnnotationProcessingTaskCreationAction
import com.android.build.gradle.tasks.Initial
import com.android.build.gradle.tasks.InternalID
import com.google.gson.GsonBuilder
import org.apache.commons.io.FileUtils
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File
import java.io.IOException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task that writes the SigningConfig information and publish it for dynamic-feature modules.
 */
open class SigningConfigWriterTask : AndroidVariantTask() {

    @Suppress("PropertyName")
    @VisibleForTesting
    internal val PERSISTED_FILE_NAME = "signing-config.json"

    @get:OutputDirectory
    var outputDirectory: Provider<Directory>? = null
        internal set

    @get:Input
    lateinit var signingConfig: CoreSigningConfig
        internal set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val out = outputDirectory
            ?: throw RuntimeException("OutputDirectory not set.")
        val outputFile = File(out.get().asFile, PERSISTED_FILE_NAME)
        val gsonBuilder = GsonBuilder()
        val gson = gsonBuilder.create()
        FileUtils.write(outputFile, gson.toJson(signingConfig))
    }

    class CreationAction(variantScope: VariantScope) :
        AnnotationProcessingTaskCreationAction<SigningConfigWriterTask>(
            variantScope,
            variantScope.getTaskName("signingConfigWriter"),
            SigningConfigWriterTask::class.java) {

        private var outputDirectory: Provider<Directory>? = null

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            outputDirectory = variantScope.artifacts.createDirectory(
                InternalArtifactType.METADATA_SIGNING_CONFIG,
                taskName)
        }

        override fun configure(task: SigningConfigWriterTask) {
            super.configure(task)

            task.outputDirectory = outputDirectory
            task.signingConfig = variantScope.variantConfiguration.signingConfig!!
        }
    }
}
