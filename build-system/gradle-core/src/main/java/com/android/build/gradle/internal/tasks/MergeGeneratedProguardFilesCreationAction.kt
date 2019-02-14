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

import com.android.SdkConstants
import com.android.build.gradle.internal.scope.AnchorOutputType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

class MergeGeneratedProguardFilesCreationAction(variantScope: VariantScope)
    : VariantTaskCreationAction<MergeFileTask>(variantScope) {

    override val name: String
        get() = variantScope.getTaskName("merge", "GeneratedProguardFiles")
    override val type: Class<MergeFileTask>
        get() = MergeFileTask::class.java

    private lateinit var outputFile: Provider<RegularFile>

    override fun preConfigure(taskName: String) {
        super.preConfigure(taskName)
        outputFile =
                variantScope.artifacts.createArtifactFile(
                    InternalArtifactType.GENERATED_PROGUARD_FILE,
                    BuildArtifactsHolder.OperationType.INITIAL,
                    taskName,
                    SdkConstants.FN_PROGUARD_TXT)
    }

    override fun configure(task: MergeFileTask) {
        super.configure(task)

        task.outputFile = outputFile

        val allClasses =
            variantScope.artifacts.getFinalArtifactFiles(AnchorOutputType.ALL_CLASSES).get()

        val proguardRulesFolder = SdkConstants.PROGUARD_RULES_FOLDER.replace('/', File.separatorChar)
        val proguardFiles = allClasses.asFileTree.filter { f ->
            val baseFolders = allClasses.files
            val baseFolder = baseFolders.first { f.startsWith(it) }
            f.toRelativeString(baseFolder)
                .toLowerCase()
                .startsWith(proguardRulesFolder)
        }

        task.inputFiles = proguardFiles
    }
}