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

import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.tasks.AnnotationProcessingTaskCreationAction
import com.android.build.gradle.tasks.Initial
import com.android.build.gradle.tasks.InternalID
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.IOException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task that writes the SigningConfig information and publish it for dynamic-feature modules.
 */
open class SigningConfigWriterTask : AndroidVariantTask() {

    @get:OutputDirectory
    @get:InternalID(InternalArtifactType.FEATURE_SIGNING_CONFIG)
    @get:Initial(out="")
    var outputDirectory: Provider<Directory>? = null
        internal set

    @get:Input
    @get:Optional
    var signingConfig: SigningConfig? = null
        internal set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val out = outputDirectory
            ?: throw RuntimeException("OutputDirectory not set.")
        SigningConfigMetadata.save(out.get().asFile, signingConfig)
    }

    class CreationAction(variantScope: VariantScope) :
        AnnotationProcessingTaskCreationAction<SigningConfigWriterTask>(
            variantScope,
            variantScope.getTaskName("signingConfigWriter"),
            SigningConfigWriterTask::class.java) {

        override fun configure(task: SigningConfigWriterTask) {
            super.configure(task)

            // convert to a serializable signing config. Objects from DSL are not serializable.
            task.signingConfig = variantScope.variantConfiguration.signingConfig?.let {
                SigningConfig(it.name).initWith(it)
            }
        }
    }
}
