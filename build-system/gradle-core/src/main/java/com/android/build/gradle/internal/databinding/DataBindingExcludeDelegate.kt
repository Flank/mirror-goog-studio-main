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

package com.android.build.gradle.internal.databinding

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import javax.inject.Inject

/**
 * Delegate to handle the list of excluded classes when data binding is on.
 *
 * An instance of this class should be a [org.gradle.api.tasks.Nested] field on the task, as well
 * as an [org.gradle.api.tasks.Optional] since this is only active if databinding or viewbinding
 * is enabled.
 *
 * Configuration of the [Property] of [DataBindingExcludeDelegate] on the task is done via
 * [configureFrom].
 */
abstract class DataBindingExcludeDelegate @Inject constructor(
    @get:Nested
    val layoutXmlProcessorDelegate: LayoutXmlProcessorDelegate,
    @get:Input
    val databindingEnabled: Boolean
) {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val exportClassListLocation: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyArtifactsDir: DirectoryProperty

    @get:Internal
    internal val excludedClassList: List<String>
        get() {
            if (!databindingEnabled) {
                return listOf()
            }

            return DataBindingBuilder.getJarExcludeList(
                layoutXmlProcessorDelegate.layoutXmlProcessor,
                exportClassListLocation.orNull?.asFile,
                dependencyArtifactsDir.get().asFile);
        }
}

fun Property<DataBindingExcludeDelegate>.configureFrom(creationConfig: BaseCreationConfig) {
    val dataBindingEnabled: Boolean = creationConfig.buildFeatures.dataBinding
    val viewBindingEnabled: Boolean = creationConfig.buildFeatures.viewBinding
    if (!dataBindingEnabled && !viewBindingEnabled) {
        return
    }

    setDisallowChanges(creationConfig.globalScope.project.provider {
        creationConfig.services.newInstance(
            DataBindingExcludeDelegate::class.java,
            LayoutXmlProcessorDelegate(
                creationConfig.variantDslInfo.originalApplicationId,
                creationConfig.services.projectOptions[BooleanOption.USE_ANDROID_X],
                creationConfig.paths.resourceBlameLogDir
            ),
            creationConfig.buildFeatures.dataBinding
        ).also {
            it.dependencyArtifactsDir.setDisallowChanges(
                creationConfig.artifacts
                    .getFinalProduct(
                        InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS
                    )
            )

            it.exportClassListLocation.setDisallowChanges(
                creationConfig.artifacts
                    .getFinalProduct(
                        InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST
                    )
            )
        }
    })

}

