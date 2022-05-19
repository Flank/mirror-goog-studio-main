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
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.dsl.FusedLibraryExtension
import com.android.build.gradle.internal.dsl.FusedLibraryExtensionImpl
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScopeImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.tasks.FusedLibraryBundleAar
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryManifestMergerTask
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class FusedLibraryPlugin @Inject constructor(
    softwareComponentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
): AbstractFusedLibraryPlugin<FusedLibraryVariantScopeImpl>(softwareComponentFactory, listenerRegistry) {

    // so far, there is only one variant.
    override val variantScope by lazy {
        withProject("variantScope") { project ->
            FusedLibraryVariantScopeImpl(
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

    override fun maybePublishToMaven(
        project: Project,
        includeApiElements: Configuration,
        includeRuntimeElements: Configuration,
        includeRuntimeUnmerged: Configuration
    ) {
        val bundleTaskProvider = variantScope
                .artifacts
                .getArtifactContainer(artifactTypeForPublication)
                .getTaskProviders()
                .last()

        val apiPublication = project.configurations.create("apiPublication").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.isVisible = false
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            it.attributes.attribute(
                Bundling.BUNDLING_ATTRIBUTE,
                project.objects.named(Bundling::class.java, Bundling.EXTERNAL)
            )
            it.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, Category.LIBRARY)
            )
            it.attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.objects.named(
                    LibraryElements::class.java,
                    AndroidArtifacts.ArtifactType.AAR.type
                )
            )
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                project.objects.named(BuildTypeAttr::class.java, "debug")
            )
            it.extendsFrom(includeApiElements)
            variantScope.outgoingConfigurations.addConfiguration(it)
            it.outgoing.artifact(bundleTaskProvider) { artifact ->
                artifact.type = AndroidArtifacts.ArtifactType.AAR.type
            }
        }

        val runtimePublication = project.configurations.create("runtimePublication").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = false
            it.isVisible = false
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            it.attributes.attribute(
                Bundling.BUNDLING_ATTRIBUTE,
                project.objects.named(Bundling::class.java, Bundling.EXTERNAL)
            )
            it.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, Category.LIBRARY)
            )
            it.attributes.attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                project.objects.named(
                    LibraryElements::class.java,
                    AndroidArtifacts.ArtifactType.AAR.type
                )
            )
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                project.objects.named(BuildTypeAttr::class.java, "debug")
            )
            it.extendsFrom(includeRuntimeElements)
            variantScope.outgoingConfigurations.addConfiguration(it)
            it.outgoing.artifact(bundleTaskProvider) { artifact ->
                artifact.type = AndroidArtifacts.ArtifactType.AAR.type
            }
        }

        // create an adhoc component, this will be used for publication
        val adhocComponent = softwareComponentFactory.adhoc("fusedLibraryComponent")
        // add it to the list of components that this project declares
        project.components.add(adhocComponent)

        adhocComponent.addVariantsFromConfiguration(apiPublication) {
            it.mapToMavenScope("compile")
        }
        adhocComponent.addVariantsFromConfiguration(runtimePublication) {
            it.mapToMavenScope("runtime")
        }

        project.afterEvaluate {
            project.extensions.findByType(PublishingExtension::class.java)?.also {
                component(
                    it.publications.create("maven", MavenPublication::class.java)
                        .also { mavenPublication ->
                            mavenPublication.from(adhocComponent)
                        }, includeRuntimeUnmerged.incoming.artifacts
                )
            }
        }
    }

    override fun createTasks(project: Project) {
        createTasks(
                project,
                variantScope,
                listOf(
                        FusedLibraryClassesRewriteTask.CreationAction(variantScope),
                        FusedLibraryManifestMergerTask.CreationAction(variantScope),
                        FusedLibraryMergeResourcesTask.CreationAction(variantScope),
                        FusedLibraryMergeClasses.CreationAction(variantScope),
                        FusedLibraryBundleClasses.CreationAction(variantScope),
                        FusedLibraryBundleAar.CreationAction(variantScope),
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope),
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType  =
        GradleBuildProject.PluginType.FUSED_LIBRARIES

    override val artifactTypeForPublication: Artifact.Single<RegularFile>
        get() = FusedLibraryInternalArtifactType.BUNDLED_LIBRARY
}
