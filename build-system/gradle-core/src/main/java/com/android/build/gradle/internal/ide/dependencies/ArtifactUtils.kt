/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ArtifactUtils")

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

/** This holder class exists to allow lint to depend on the artifact collections. */
class ArtifactCollections(
    componentProperties: ComponentPropertiesImpl,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType
) {
    /**
     * A collection containing 'all' artifacts, i.e. jar and AARs from subprojects, repositories
     * and files.
     *
     * This will give the following mapping:
     * * Java library project → Untransformed jar output
     * * Android library project → *jar* output, aar is not published between projects.
     *   This could be a separate type in the future if it was desired not to publish the jar from
     *   android-library projects.
     * * Remote jar → Untransformed jar
     * * Remote aar → Untransformed aar
     * * Local jar → untransformed jar
     * * Local aar → untransformed aar
     * * Jar wrapped as a project → untransformed aar
     * * aar wrapped as a project → untransformed aar
     *
     * Using an artifact view as that contains local dependencies, unlike
     * `configuration.incoming.resolutionResult` which only contains project and \[repository\]
     * module dependencies.
     *
     * This captures dependencies without transforming them using `AttributeCompatibilityRule`s.
     **/
    val all: ArtifactCollection = componentProperties.variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.AAR_OR_JAR
    )

    val manifests: ArtifactCollection = componentProperties.variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.MANIFEST
    )
    val nonNamespacedManifests: ArtifactCollection? =
        if (componentProperties.globalScope.extension.aaptOptions.namespaced) {
            componentProperties.variantDependencies.getArtifactCollectionForToolingModel(
                consumedConfigType,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST
            )
        } else {
            null
        }

    // We still need to understand wrapped jars and aars. The former is difficult (TBD), but
    // the latter can be done by querying for EXPLODED_AAR. If a sub-project is in this list,
    // then we need to override the type to be external, rather than sub-project.
    // This is why we query for Scope.ALL
    // But we also simply need the exploded AARs for external Android dependencies so that
    // Studio can access the content.
    val explodedAars: ArtifactCollection = componentProperties.variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.ALL,
        AndroidArtifacts.ArtifactType.EXPLODED_AAR
    )

    // Note: Query for JAR instead of PROCESSED_JAR for project dependencies due to b/110054209
    // With a solution to that projectJars and externalJars could be merged.
    val projectJars: ArtifactCollection = componentProperties.variantDependencies.getArtifactCollectionForToolingModel(
        consumedConfigType,
        AndroidArtifacts.ArtifactScope.PROJECT,
        AndroidArtifacts.ArtifactType.JAR
    )

    val allCollections: Collection<ArtifactCollection>
        get() = listOfNotNull(
            all,
            manifests,
            nonNamespacedManifests,
            explodedAars,
            projectJars
        )
}

/**
 * Returns a set of ResolvedArtifact where the [ResolvedArtifact.dependencyType] and
 * [ResolvedArtifact.isWrappedModule] fields have been setup properly.
 *
 * @param componentProperties the variant to get the artifacts from
 * @param consumedConfigType the type of the dependency to resolve (compile vs runtime)
 * @param dependencyFailureHandler handler for dependency resolution errors
 * @param buildMapping a build mapping from build name to root dir.
 */
fun getAllArtifacts(
    componentProperties: ComponentPropertiesImpl,
    consumedConfigType: AndroidArtifacts.ConsumedConfigType,
    dependencyFailureHandler: DependencyFailureHandler?,
    buildMapping: ImmutableMap<String, String>
): Set<ResolvedArtifact> {
    // FIXME change the way we compare dependencies b/64387392

    // we need to figure out the following:
    // - Is it an external dependency or a sub-project?
    // - Is it an android or a java dependency

    val collections = ArtifactCollections(componentProperties, consumedConfigType)

    // All artifacts: see comment on collections.all
    val incomingArtifacts = collections.all

    // Then we can query for MANIFEST that will give us only the Android project so that we
    // can detect JAVA vs ANDROID.
    val manifests = if (collections.nonNamespacedManifests != null) {
        ImmutableMultimap.builder<ComponentIdentifier, ResolvedArtifactResult>()
            .putAll(collections.manifests.asMultiMap())
            .putAll(collections.nonNamespacedManifests.asMultiMap()).build()
    } else {
        collections.manifests.asMultiMap()
    }

    val explodedAars = collections.explodedAars.asMultiMap()

    // Note: Query for JAR instead of PROCESSED_JAR for project dependencies due to b/110054209
    // With a solution to that projectJars and externalJars could be merged.
    val projectJars = collections.projectJars.asMultiMap()

    // collect dependency resolution failures
    if (dependencyFailureHandler != null) {
        val failures = incomingArtifacts.failures
        // compute the name of the configuration
        dependencyFailureHandler.addErrors(
            componentProperties.globalScope.project.path
                    + "@"
                    + componentProperties.name
                    + "/"
                    + consumedConfigType.getName(),
            failures
        )
    }

    // build a list of wrapped AAR, and a map of all the exploded-aar artifacts
    val aarWrappedAsProjects = explodedAars.keySet().filterIsInstance<ProjectComponentIdentifier>()

    // build a list of android dependencies based on them publishing a MANIFEST element

    // build the final list, using the main list augmented with data from the previous lists.
    val resolvedArtifactResults = incomingArtifacts.artifacts

    // use a linked hash set to keep the artifact order.
    val artifacts =
        Sets.newLinkedHashSetWithExpectedSize<ResolvedArtifact>(resolvedArtifactResults.size)

    for (resolvedComponentResult in resolvedArtifactResults) {
        val componentIdentifier = resolvedComponentResult.id.componentIdentifier

        // check if this is a wrapped module
        val isAarWrappedAsProject = aarWrappedAsProjects.contains(componentIdentifier)

        // check if this is an android external module. In this case, we want to use the exploded
        // aar as the artifact we depend on rather than just the JAR, so we swap out the
        // ResolvedArtifactResult.
        val dependencyType: ResolvedArtifact.DependencyType

        val extractedAar: Collection<ResolvedArtifactResult> = explodedAars[componentIdentifier]

        val manifest: Collection<ResolvedArtifactResult> = manifests[componentIdentifier]

        val mainArtifacts: Collection<ResolvedArtifactResult>

        val artifactType =
            resolvedComponentResult.variant.attributes.getAttribute(AndroidArtifacts.ARTIFACT_TYPE)
        when (artifactType) {
            AndroidArtifacts.ArtifactType.AAR.type -> {
                // This only happens for external dependencies - local android libraries do not
                // publish the AAR between projects.
                dependencyType = ResolvedArtifact.DependencyType.ANDROID
                mainArtifacts = listOf(resolvedComponentResult)
            }
            AndroidArtifacts.ArtifactType.JAR.type ->
                if (manifest.isNotEmpty()) {
                    dependencyType = ResolvedArtifact.DependencyType.ANDROID
                    mainArtifacts = manifest
                } else {
                    dependencyType = ResolvedArtifact.DependencyType.JAVA
                    val projectJar = projectJars[componentIdentifier]
                    mainArtifacts = if (projectJar.isNotEmpty()) {
                        projectJar
                    } else {
                        // Note use this component directly to handle classified artifacts
                        // This is tested by AppWithClassifierDepTest.
                        listOf<ResolvedArtifactResult>(resolvedComponentResult)
                    }
                }
            else -> throw IllegalStateException("Internal error: Artifact type $artifactType not expected, only jar or aar are handled.")

        }

        check(mainArtifacts.isNotEmpty()) {
            """Internal Error: No artifact found for artifactType '$componentIdentifier'
            | context: ${componentProperties.globalScope.project.path} ${componentProperties.name}
            | manifests = $manifests
            | explodedAars = $explodedAars
            | projectJars = $projectJars
        """.trimMargin()
        }

        for (mainArtifact in mainArtifacts) {
            artifacts.add(
                ResolvedArtifact(
                    mainArtifact,
                    extractedAar.firstOrNull(),
                    dependencyType,
                    isAarWrappedAsProject,
                    buildMapping
                )
            )
        }
    }

    return artifacts
}

/**
 * This is a multi map to handle when there are multiple jars with the same component id.
 *
 * e.g. see `AppWithClassifierDepTest`
 */
fun ArtifactCollection.asMultiMap(): ImmutableMultimap<ComponentIdentifier, ResolvedArtifactResult> {
    return ImmutableMultimap.builder<ComponentIdentifier, ResolvedArtifactResult>()
        .also { builder ->
            for (artifact in artifacts) {
                builder.put(artifact.id.componentIdentifier, artifact)
            }
        }.build()
}