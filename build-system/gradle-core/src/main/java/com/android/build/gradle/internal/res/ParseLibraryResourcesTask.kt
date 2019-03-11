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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.Workers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task for parsing local library resources. It generates the local R-def.txt file containing the
 * symbols (see SymbolIo.writeRDef for the format), which is used by the GenerateLibraryRFileTask
 * to merge with the dependencies R.txt files to generate the R.txt for this module and the R.jar
 * for the universe.
 *
 * TODO(imorlowska): Make this incremental (at least in the easy cases for now).
 * TODO(imorlowska): Refactor the parsers to work with workers, so we can parse files in parallel.
 */
@CacheableTask
open class ParseLibraryResourcesTask @Inject constructor(workerExecutor: WorkerExecutor)
    : AndroidVariantTask() {
    private val workers = Workers.getWorker(project.name, path, workerExecutor)

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var platformAttrRTxt: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var inputResourcesDir: BuildableArtifact
        private set

    @get:OutputFile
    lateinit var librarySymbolsFile: File
        private set

    @TaskAction
    fun parseResources() {
        workers.use {
            it.submit(
                ParseResourcesRunnable::class.java,
                ParseResourcesParams(
                    inputResDir = inputResourcesDir.singleFile(),
                    platformAttrsRTxt = platformAttrRTxt.singleFile,
                    librarySymbolsFile = librarySymbolsFile
                )
            )
        }
    }

    data class ParseResourcesParams(
        val inputResDir: File,
        val platformAttrsRTxt: File,
        val librarySymbolsFile: File
    ) : Serializable

    class ParseResourcesRunnable @Inject constructor(private val params: ParseResourcesParams
    ) : Runnable {
        override fun run() {
            // IDs do not matter as we will merge all symbols and re-number them in the
            // GenerateLibraryRFileTask anyway. Give a fake package for the same reason.
            val symbolTable = parseResourceSourceSetDirectory(
                params.inputResDir,
                IdProvider.constant(),
                getAndroidAttrSymbols(),
                "local"
            )

            // Write in the format of R-def.txt since the IDs do not matter. The symbols will be
            // written in a deterministic way (sorted by type, then by canonical name).
            SymbolIo.writeRDef(symbolTable, params.librarySymbolsFile.toPath())
        }

        private fun getAndroidAttrSymbols() =
            if (params.platformAttrsRTxt.exists())
                SymbolIo.readFromAapt(params.platformAttrsRTxt, "android")
            else
                SymbolTable.builder().tablePackage("android").build()
    }

    class CreateAction(
        variantScope: VariantScope
    ): VariantTaskCreationAction<ParseLibraryResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("parse", "LibraryResources")
        override val type: Class<ParseLibraryResourcesTask>
            get() = ParseLibraryResourcesTask::class.java

        private lateinit var librarySymbolsFile: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            librarySymbolsFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                taskName,
                SdkConstants.FN_R_DEF_TXT)
        }

        override fun configure(task: ParseLibraryResourcesTask) {
            super.configure(task)

            task.platformAttrRTxt = variantScope.globalScope.platformAttrs

            task.inputResourcesDir = variantScope.artifacts.getFinalArtifactFiles(
                InternalArtifactType.PACKAGED_RES)

            task.librarySymbolsFile = librarySymbolsFile
        }
    }
}