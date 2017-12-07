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
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.SymbolIo
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
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
open class GenerateNamespacedLibraryRFilesTask : AndroidBuilderTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var rDotTxtCollection: FileCollection private set

    @get:Internal lateinit var packageForRSupplier: Supplier<String> private set
    @get:Input private val packageForR get() = packageForRSupplier.get()

    @get:OutputFile lateinit var rJarFile: File private set
    @get:OutputFile lateinit var resIdsFile: File private set

    @TaskAction
    fun taskAction() {
        val rDotTxt = rDotTxtCollection.singleFile
        Preconditions.checkArgument(rDotTxt.exists(), "R.txt does not exist at path: " + rDotTxt.absolutePath)

        FileUtils.deleteIfExists(rJarFile)
        FileUtils.deleteIfExists(resIdsFile)

        // Read the symbol table from the R.txt file.
        val resources = SymbolIo.readFromAapt(rDotTxt, packageForR)

        // Generate the R.jar file containing compiled R class and its' subclasses.
        exportToCompiledJava(ImmutableList.of(resources), rJarFile.toPath())

        // Finally, generate the res-ids.txt file containing the package name and the resources list.
        SymbolIo.writeSymbolTableWithPackage(rDotTxt.toPath(), packageForR, resIdsFile.toPath())
    }

    class ConfigAction(
            private val scope: VariantScope,
            private val rDotTxt: FileCollection,
            private val rJarFile: File,
            private val resIdsFile: File) : TaskConfigAction<GenerateNamespacedLibraryRFilesTask> {

        override fun getType() = GenerateNamespacedLibraryRFilesTask::class.java

        override fun getName() = scope.getTaskName("create", "RFiles")

        override fun execute(task: GenerateNamespacedLibraryRFilesTask) {
            task.variantName = scope.fullVariantName
            task.setAndroidBuilder(scope.globalScope.androidBuilder)

            task.rDotTxtCollection = rDotTxt
            task.packageForRSupplier = Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
            task.rJarFile = rJarFile
            task.resIdsFile = resIdsFile
        }
    }
}