/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** Data about a variant that produce a Library bundle (.aar)  */
class LibraryVariantData(
    componentIdentity: ComponentIdentity,
    variantDslInfo: LibraryVariantDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    services: VariantServices,
    taskContainer: MutableTaskContainer
) : BaseVariantData(
    componentIdentity,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    services,
    taskContainer
), TestedVariantData {
    private val testVariants: MutableMap<ComponentType, TestVariantData> = mutableMapOf()

    override val description: String
        get() = if (componentIdentity.productFlavors.isNotEmpty()) {
            val sb = StringBuilder(50)
            componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.append(" build for flavor ")
            componentIdentity.flavorName?.let { sb.appendCapitalized(it) }
            sb.toString()
        } else {
            componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }

    override fun getTestVariantData(type: ComponentType): TestVariantData? {
        return testVariants[type]
    }

    override fun setTestVariantData(testVariantData: TestVariantData, type: ComponentType) {
        testVariants[type] = testVariantData
    }

    // Overridden to add source folders to a generateAnnotationsTask, if it exists.
    override fun registerJavaGeneratingTask(
        taskProvider: TaskProvider<out Task>,
        generatedSourceFolders: Collection<File>
    ) {
        super.registerJavaGeneratingTask(taskProvider, generatedSourceFolders)
        addSourcesToGenerateAnnotationsTask(generatedSourceFolders)
    }

    // TODO: remove and use a normal dependency on the final list of source files.
    private fun addSourcesToGenerateAnnotationsTask(sourceFolders: Collection<File>) {
        taskContainer.generateAnnotationsTask?.let { taskProvider ->
            taskProvider.configure { task ->
                for (f in sourceFolders) {
                    task.source(f)
                }
            }
        }
    }
}
