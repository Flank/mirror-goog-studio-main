/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.Artifact
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.tasks.FusedLibraryBundleAar
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryManifestMergerTask
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class FusedLibraryPlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
): AbstractFusedLibraryPlugin<FusedLibraryVariantScope>(softwareComponentFactory, listenerRegistry) {

    // so far, there is only one variant.
    override val variantScope by lazy {
        withProject("variantScope") { project ->
            FusedLibraryVariantScope(
                project
            ) { extension }
        }
    }

    private val extension: FusedLibraryExtension by lazy {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
    }

    override fun configureExtension(project: Project) {
        extension
    }

    private fun instantiateExtension(project: Project): FusedLibraryExtension {

        val fusedLibraryExtensionImpl= dslServices.newDecoratedInstance(
            FusedLibraryExtensionImpl::class.java,
            dslServices,
        )

        abstract class Extension(
            val publicExtensionImpl: FusedLibraryExtensionImpl,
        ): FusedLibraryExtension by publicExtensionImpl

        return project.extensions.create(
            FusedLibraryExtension::class.java,
            "android",
            Extension::class.java,
            fusedLibraryExtensionImpl
        )

    }

    override fun createTasks(project: Project) {
        createTasks(
                project,
                variantScope,
                listOf(
                        FusedLibraryClassesRewriteTask.CreateAction(variantScope),
                        FusedLibraryManifestMergerTask.CreationAction(variantScope),
                        FusedLibraryMergeResourcesTask.CreationAction(variantScope),
                        FusedLibraryMergeClasses.CreationAction(variantScope),
                        FusedLibraryBundleClasses.CreationAction(variantScope),
                        FusedLibraryBundleAar.CreationAction(variantScope),
                ),
        )

        TaskFactoryImpl(project.tasks).let { taskFactory ->
            FusedLibraryMergeArtifactTask.getCreationActions(variantScope).forEach {
                taskFactory.register(it)
            }
        }
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType  =
        GradleBuildProject.PluginType.FUSED_LIBRARIES

    override val artifactTypeForPublication: Artifact.Single<RegularFile>
        get() = FusedLibraryInternalArtifactType.BUNDLED_LIBRARY
}
