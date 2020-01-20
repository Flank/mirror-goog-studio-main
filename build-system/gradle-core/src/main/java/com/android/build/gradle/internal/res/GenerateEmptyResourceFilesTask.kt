/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.symbols.SymbolTable
import com.android.utils.FileUtils
import com.google.common.base.Strings
import com.google.common.collect.ImmutableList
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 *  Task to create an empty R.txt, an empty res/ directory and an empty R class.
 *
 *  This task is used when resource processing in an Android Library module is disabled. Instead of
 *  multiple tasks merging, parsing and processing resources, the user can fully disable the
 *  resource pipeline in a library module and have this task generate the empty artifacts instead.
 *
 *  The R.txt and res/ directory are required artifacts in an AAR, even when empty, so we still need
 *  to generate them. We can however skip generating the public.txt, since it's not required
 *  (missing public.txt means all resources in the R.txt in that AAR are public, but since the R.txt
 *  is empty, we can safely skip the public.txt file).
 */
@CacheableTask
abstract class GenerateEmptyResourceFilesTask : NonIncrementalTask() {

    @get:Input
    abstract val packageForR: Property<String>

    @get:OutputFile
    abstract val emptyRDotTxt: RegularFileProperty

    @get:OutputFile
    abstract val emptyRJar: RegularFileProperty

    @get:OutputDirectory
    abstract val emptyMergedResources: DirectoryProperty

    override fun doTaskAction() {
        // TODO(147579629): should this contain transitive resources or is it okay to have it empty?
        // Create empty R.txt, will be used for bundling in the AAR.
        emptyRDotTxt.asFile.get().writeText("")

        // Create empty res/ directory to bundle in the AAR.
        FileUtils.mkdirs(emptyMergedResources.asFile.get())

        // And finally create an empty R.jar.
        val emptySymbolTable = SymbolTable.FastBuilder()
        emptySymbolTable.tablePackage(packageForR.get())
        exportToCompiledJava(
            ImmutableList.of(emptySymbolTable.build()),
            emptyRJar.asFile.get().toPath())
    }

    class CreateAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<GenerateEmptyResourceFilesTask, ComponentPropertiesImpl>(
            componentProperties
        ) {

        override val name: String
            get() = component.computeTaskName("generate", "EmptyResourceFiles")
        override val type: Class<GenerateEmptyResourceFilesTask>
            get() = GenerateEmptyResourceFilesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out GenerateEmptyResourceFilesTask>) {
            super.handleProvider(taskProvider)
            component.artifacts.producesFile(
                InternalArtifactType.COMPILE_SYMBOL_LIST,
                taskProvider,
                GenerateEmptyResourceFilesTask::emptyRDotTxt,
                SdkConstants.FN_RESOURCE_TEXT
            )

            component.artifacts.producesDir(
                InternalArtifactType.PACKAGED_RES,
                taskProvider,
                GenerateEmptyResourceFilesTask::emptyMergedResources,
                SdkConstants.FD_RES
            )

            component.artifacts.producesFile(
                InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR,
                taskProvider,
                GenerateEmptyResourceFilesTask::emptyRJar,
                SdkConstants.FN_INTERMEDIATE_RES_JAR
            )
        }

        override fun configure(task: GenerateEmptyResourceFilesTask) {
            super.configure(task)
            task.packageForR.setDisallowChanges(task.project.provider {
                Strings.nullToEmpty(component.variantDslInfo.originalApplicationId)
            })
        }
    }
}