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
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.fusedlibrary.SegregatingConstraintHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import groovy.namespace.QName
import groovy.util.Node
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier

abstract class AbstractFusedLibraryPlugin<SCOPE: FusedLibraryVariantScope>(
    protected val softwareComponentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    val dslServices by lazy {
        withProject("dslServices") { project ->
            val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
                SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
                ).execute()

            DslServicesImpl(
                projectServices,
                sdkComponentsBuildService
            )
        }
    }

    abstract val variantScope: SCOPE

    inline fun <reified TASK_SCOPE: FusedLibraryVariantScope> createTasks(
        project: Project,
        variantScope: TASK_SCOPE,
        tasksCreationActions: List<TaskCreationAction<out DefaultTask>>,
    ) {
        configureTransforms(project)
        val taskProviders = TaskFactoryImpl(project.tasks).let { taskFactory ->
            tasksCreationActions.map { creationAction ->
                taskFactory.register(creationAction)
            }
        }

        // create anchor tasks
        project.tasks.register("assemble") { assembleTask ->
            artifactForPublication?.let { artifactTypeForPublication ->
                assembleTask.dependsOn(variantScope.artifacts.get(artifactTypeForPublication))
            } ?: taskProviders.forEach { assembleTask.dependsOn(it) }
        }
    }

    /**
     * Returns the artifact type that will be used for maven publication or null if nothing is to
     * be published to maven.
     */
    abstract val artifactForPublication: Artifact.Single<RegularFile>?

    abstract val artifactTypeForPublication: AndroidArtifacts.ArtifactType

    abstract val allowUnmergedArtifacts: Boolean

    override fun apply(project: Project) {
        super.basePluginApply(project)

        // so far by default, we consume and publish only 'debug' variant

        // 'include' is the configuration that users will use to indicate which dependencies should
        // be fused.
        val includeConfigurations = project.configurations.create("include").also {
            it.isCanBeConsumed = false
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )
        }

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_API usage which mean all transitive
        // dependencies that are implementation() scoped will not be included.
        val includeApiClasspath = project.configurations.create("includeApiClasspath").also {
            it.isCanBeConsumed = false
            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )
            it.extendsFrom(includeConfigurations)
        }
        variantScope.incomingConfigurations.addConfiguration(includeApiClasspath)

        // This is the configuration that will contain all the JAVA_API dependencies that are not
        // fused in the resulting aar library.
        val includedApiUnmerged = project.configurations.create("includeApiUnmerged").also {
            it.isCanBeConsumed = true
            it.isCanBeResolved = true
            it.incoming.beforeResolve(
                SegregatingConstraintHandler(
                    includeApiClasspath,
                    it,
                    variantScope.mergeSpec,
                    project,
                )
            )
        }

        // This is the internal configuration that will be used to feed tasks that require access
        // to the resolved 'include' dependency. It is for JAVA_RUNTIME usage which mean all transitive
        // dependencies that are implementation() scoped will  be included.
        val includeRuntimeClasspath = project.configurations.create("includeRuntimeClasspath").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true

            it.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            val buildType: BuildTypeAttr = project.objects.named(BuildTypeAttr::class.java, "debug")
            it.attributes.attribute(
                BuildTypeAttr.ATTRIBUTE,
                buildType,
            )

            it.extendsFrom(includeConfigurations)
        }
        variantScope.incomingConfigurations.addConfiguration(includeRuntimeClasspath)

        // This is the configuration that will contain all the JAVA_RUNTIME dependencies that are
        // not fused in the resulting aar library.
        val includeRuntimeUnmerged = project.configurations.create("includeRuntimeUnmerged").also {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.incoming.beforeResolve(
                SegregatingConstraintHandler(
                    includeConfigurations,
                    it,
                    variantScope.mergeSpec,
                    project,
                )
            )
        }

        // we are only interested in the last provider in the chain of transformers for this bundle.
        // Obviously, this is theoretical at this point since there is no variant API to replace
        // artifacts, there is always only one.
        val bundleTaskProvider = artifactForPublication?.let {
            variantScope
                .artifacts
                .getArtifactContainer(it)
                .getTaskProviders()
                .last()
        }

        // this is the outgoing configuration for JAVA_API scoped declarations, it will contain
        // this module and all transitive non merged dependencies

        fun configureApiRuntimeElements(elements: Configuration) {
            elements.isCanBeResolved = false
            elements.isCanBeConsumed = true
            elements.isTransitive = true

            if (bundleTaskProvider != null) {
                elements.outgoing.variants { variants ->
                    variants.create(artifactTypeForPublication.type)  {variant ->
                        variant.artifact(bundleTaskProvider) { artifact ->
                            artifact.type = artifactTypeForPublication.type
                        }
                    }
                }
            }
        }

        val includeApiElements = project.configurations.create("apiElements") { apiElements ->
            configureApiRuntimeElements(apiElements)
            apiElements.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_API)
            )
            if (allowUnmergedArtifacts) {
                apiElements.extendsFrom(includedApiUnmerged)
            }
        }

        // this is the outgoing configuration for JAVA_RUNTIME scoped declarations, it will contain
        // this module and all transitive non merged dependencies
        val includeRuntimeElements = project.configurations.create("runtimeElements") { runtimeElements ->
            configureApiRuntimeElements(runtimeElements)
            runtimeElements.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )
            if (allowUnmergedArtifacts) {
                runtimeElements.extendsFrom(includeRuntimeUnmerged)
            }
        }

        maybePublishToMaven(
            project,
            includeApiElements,
            includeRuntimeElements,
            includeRuntimeUnmerged
        )
    }

    override fun configureProject(project: Project) {
        val projectOptions = projectServices.projectOptions
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
    }

    protected abstract fun maybePublishToMaven(
        project: Project,
        includeApiElements: Configuration,
        includeRuntimeElements: Configuration,
        includeRuntimeUnmerged: Configuration
    )

    fun component(
        publication: MavenPublication,
        unmergedArtifacts: ArtifactCollection,
    ) {

        publication.pom {  pom: MavenPom ->
            pom.withXml { xml ->
                val dependenciesNode = xml.asNode().let {
                    it.children().firstOrNull { node ->
                        ((node as Node).name() as QName).qualifiedName == "dependencies"
                    } ?: it.appendNode("dependencies")
                } as Node

                unmergedArtifacts.forEach { artifact ->
                    if (artifact.id is ModuleComponentArtifactIdentifier) {
                        when (val moduleIdentifier = artifact.id.componentIdentifier) {
                            is ModuleComponentIdentifier -> {
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", moduleIdentifier.group)
                                dependencyNode.appendNode("artifactId", moduleIdentifier.module)
                                dependencyNode.appendNode("version", moduleIdentifier.version)
                                dependencyNode.appendNode("scope", "runtime")
                            }
                            is ProjectComponentIdentifier -> println("Project : ${moduleIdentifier.projectPath}")
                            is LibraryBinaryIdentifier -> println("Library : ${moduleIdentifier.projectPath}")
                            else -> println("Unknown dependency ${moduleIdentifier.javaClass} : $artifact")
                        }
                    } else {
                        println("Unknown module ${artifact.id.javaClass} : ${artifact.id}")
                    }
                }
            }
        }
    }

    fun configureTransforms(project: Project) {
        configuratorService.recordBlock(
                GradleBuildProfileSpan.ExecutionType.ARTIFACT_TRANSFORM,
                project.path,
                null
        ) {
            DependencyConfigurator(project, projectServices)
                    .configureGeneralTransforms(namespacedAndroidResources = false)
        }
    }
}
