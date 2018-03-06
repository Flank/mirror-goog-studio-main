/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.utils.FileUtils
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.function.Supplier

/*
 * Class generating the R.jar and res-ids.txt files for a resource namespace aware library.
 */
@CacheableTask
open class GenerateNamespacedLibraryRFilesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var partialRFilesCollection: FileCollection private set

    @get:Internal lateinit var packageForRSupplier: Supplier<String> private set
    @get:Input val packageForR get() = packageForRSupplier.get()

    @get:OutputFile lateinit var rJarFile: File private set
    @get:OutputFile lateinit var resIdsFile: File private set

    @TaskAction
    fun taskAction() {
        // Keeping the order is important.
        val partialRFiles = ImmutableList.builder<File>()
        partialRFilesCollection.forEach { directory ->
           partialRFiles.addAll(directory.listFiles{ f -> f.isFile }.asIterable())
        }

        FileUtils.deleteIfExists(rJarFile)
        FileUtils.deleteIfExists(resIdsFile)

        // Read the symbol tables from the partial R.txt files and merge them into one.
        val resources = SymbolTable.mergePartialTables(partialRFiles.build(), packageForR)

        // Generate the R.jar file containing compiled R class and its' inner classes.
        exportToCompiledJava(ImmutableList.of(resources), rJarFile.toPath())

        // Finally, generate the res-ids.txt file containing the package name and the resources list.
        SymbolIo.writeRDef(resources, resIdsFile.toPath())
    }

    class ConfigAction(
            private val scope: VariantScope,
            private val partialRFiles: FileCollection,
            private val rJarFile: File,
            private val resIdsFile: File) : TaskConfigAction<GenerateNamespacedLibraryRFilesTask> {

        override fun getType() = GenerateNamespacedLibraryRFilesTask::class.java

        override fun getName() = scope.getTaskName("create", "RFiles")

        override fun execute(task: GenerateNamespacedLibraryRFilesTask) {

            task.partialRFilesCollection = partialRFiles
            task.packageForRSupplier =
                    Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
            task.rJarFile = rJarFile
            task.resIdsFile = resIdsFile
        }
    }
}