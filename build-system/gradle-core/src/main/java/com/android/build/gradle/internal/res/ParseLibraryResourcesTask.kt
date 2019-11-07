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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.getChangesInSerializableForm
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.ResourceDirectoryParseException
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.parseResourceFile
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Task for parsing local library resources. It generates the local R-def.txt file containing the
 * symbols (see SymbolIo.writeRDef for the format), which is used by the GenerateLibraryRFileTask
 * to merge with the dependencies R.txt files to generate the R.txt for this module and the R.jar
 * for the universe.
 *
 * TODO(imorlowska): Refactor the parsers to work with workers, so we can parse files in parallel.
 */
@CacheableTask
abstract class ParseLibraryResourcesTask : NewIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var platformAttrRTxt: FileCollection
        private set

    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResourcesDir: DirectoryProperty

    @get:OutputFile
    abstract val librarySymbolsFile: RegularFileProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        val incremental = inputChanges.isIncremental
        val changedResources = if (incremental) {
            // This method already ignores directories, only actual file changes will be reported.
            inputChanges.getChangesInSerializableForm(inputResourcesDir).changes
        } else {
            listOf()
        }

        getWorkerFacadeWithWorkers().use {
            it.submit(
                ParseResourcesRunnable::class.java,
                ParseResourcesParams(
                    inputResDir = inputResourcesDir.get().asFile,
                    platformAttrsRTxt = platformAttrRTxt.singleFile,
                    librarySymbolsFile = librarySymbolsFile.get().asFile,
                    incremental = incremental,
                    changedResources = changedResources
                )
            )
        }
    }

    data class ParseResourcesParams(
        val inputResDir: File,
        val platformAttrsRTxt: File,
        val librarySymbolsFile: File,
        val incremental: Boolean,
        val changedResources: Collection<SerializableChange>
    ) : Serializable

    class ParseResourcesRunnable @Inject constructor(private val params: ParseResourcesParams
    ) : Runnable {
        override fun run() {
            if (!params.incremental
                || !params.changedResources.all { canBeProcessedIncrementally(it) }) {
                // Non-incremental run.
                doFullTaskAction(params)
            } else {
                // All files can be processed incrementally, update the existing table.
                doIncrementalTaskAction(params)
            }
        }
    }

    class CreateAction(
        variantScope: VariantScope
    ): VariantTaskCreationAction<ParseLibraryResourcesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("parse", "LocalResources")
        override val type: Class<ParseLibraryResourcesTask>
            get() = ParseLibraryResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ParseLibraryResourcesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                taskProvider,
                ParseLibraryResourcesTask::librarySymbolsFile,
                SdkConstants.FN_R_DEF_TXT
            )
        }

        override fun configure(task: ParseLibraryResourcesTask) {
            super.configure(task)

            task.platformAttrRTxt = variantScope.globalScope.platformAttrs

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.inputResourcesDir
            )
        }
    }

    companion object {
        internal fun canGenerateSymbols(type: ResourceFolderType, file: File) =
            type == ResourceFolderType.VALUES
                    || (FolderTypeRelationship.isIdGeneratingFolderType(type)
                    && file.name.endsWith(SdkConstants.DOT_XML, ignoreCase = true))


        internal fun canBeProcessedIncrementally(fileChange: SerializableChange): Boolean {
            if (fileChange.fileStatus == FileStatus.REMOVED) {
                // Removed files are not supported
                return false
            }
            if (fileChange.fileStatus == FileStatus.CHANGED) {
                val file = fileChange.file
                val folderType = ResourceFolderType.getFolderType(file.parentFile.name)
                    ?: error("Invalid type '${file.parentFile.name}' for file ${file.absolutePath}")
                if (canGenerateSymbols(folderType, file)) {
                    // ID generating files (e.g. values or XML layouts) can generate resources
                    // within them, if they were modified we cannot tell if a resource was removed
                    // so we need to reprocess everything.
                    return false
                }
            }
            return true
        }
    }
}

internal fun doFullTaskAction(parseResourcesParams: ParseLibraryResourcesTask.ParseResourcesParams) {
    // IDs do not matter as we will merge all symbols and re-number them in the
    // GenerateLibraryRFileTask anyway. Give a fake package for the same reason.
    val symbolTable = parseResourceSourceSetDirectory(
      parseResourcesParams.inputResDir,
      IdProvider.constant(),
      getAndroidAttrSymbols(parseResourcesParams.platformAttrsRTxt),
      "local"
    )

    // Write in the format of R-def.txt since the IDs do not matter. The symbols will be
    // written in a deterministic way (sorted by type, then by canonical name).
    SymbolIo.writeRDef(symbolTable, parseResourcesParams.librarySymbolsFile.toPath())
}

internal fun doIncrementalTaskAction(parseResourcesParams: ParseLibraryResourcesTask.ParseResourcesParams) {
    // Read the symbols from the previous run.
    val currentSymbols = SymbolIo.readRDef(parseResourcesParams.librarySymbolsFile.toPath())
    val newSymbols = SymbolTable.builder().tablePackage("local")
    val platformSymbols = getAndroidAttrSymbols(parseResourcesParams.platformAttrsRTxt)

    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    val documentBuilder = try {
        documentBuilderFactory.newDocumentBuilder()
    } catch (e: ParserConfigurationException) {
        throw ResourceDirectoryParseException("Failed to instantiate DOM parser", e)
    }

    parseResourcesParams.changedResources.forEach { fileChange ->
        val file = fileChange.file
        val type = ResourceFolderType.getFolderType(file.parentFile.name)!!
        // Values and ID generating resources (e.g. layouts) that have a FileStatus
        // different from NEW should have already been filtered out by
        // [canBeProcessedIncrementally].
        // For all other resources (that don't define other resources within them) we just
        // need to reprocess them if they're new - if only their contents changed we don't
        // need to do anything.
        if (fileChange.fileStatus == FileStatus.NEW) {
            parseResourceFile(file, type, newSymbols, documentBuilder, platformSymbols)
        }
    }

    // If we found at least one new symbol we need to update the R.txt
    if (!newSymbols.isEmpty()) {
        newSymbols.addAllIfNotExist(currentSymbols.symbols.values())
        Files.delete(parseResourcesParams.librarySymbolsFile.toPath())
        SymbolIo.writeRDef(newSymbols.build(), parseResourcesParams.librarySymbolsFile.toPath())
    }
}

private fun getAndroidAttrSymbols(platformAttrsRTxt: File) =
  if (platformAttrsRTxt.exists())
      SymbolIo.readFromAapt(platformAttrsRTxt, "android")
  else
      SymbolTable.builder().tablePackage("android").build()
