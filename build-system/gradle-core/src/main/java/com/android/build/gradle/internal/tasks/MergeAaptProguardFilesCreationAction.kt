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

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/** Configuration action for a task to merge aapt proguard files  */
class MergeAaptProguardFilesCreationAction(variantScope: VariantScope) :
    VariantTaskCreationAction<MergeFileTask>(variantScope) {

    override val name: String
        get() = variantScope.getTaskName("merge", "AaptProguardFiles")
    override val type: Class<MergeFileTask>
        get() = MergeFileTask::class.java

    private lateinit var outputFile: Provider<RegularFile>

    override fun preConfigure(taskName: String) {
        super.preConfigure(taskName)
        outputFile =
                variantScope.artifacts.createArtifactFile(
                        InternalArtifactType.MERGED_AAPT_PROGUARD_FILE,
                        BuildArtifactsHolder.OperationType.APPEND,
                        taskName,
                        SdkConstants.FN_MERGED_AAPT_RULES)
    }

    override fun configure(task: MergeFileTask) {
        super.configure(task)

        task.outputFile = outputFile
        val project = variantScope.globalScope.project
        val inputFiles =
            project
                .files(
                    variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.AAPT_PROGUARD_FILE),
                    variantScope.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.AAPT_PROGUARD_RULES
                    )
                )
        task.inputFiles = inputFiles
    }
}
