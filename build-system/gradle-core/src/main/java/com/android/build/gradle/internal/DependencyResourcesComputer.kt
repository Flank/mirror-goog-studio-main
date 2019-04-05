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
package com.android.build.gradle.internal

import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessApplicationManifest
import com.android.builder.core.BuilderConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceSet
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import java.io.File

class DependencyResourcesComputer {
    @set:VisibleForTesting
    var resources: Map<String, BuildableArtifact>? = null

    @set:VisibleForTesting
    var libraries: ArtifactCollection? = null

    @set:VisibleForTesting
    var renderscriptResOutputDir: FileCollection? = null

    @set:VisibleForTesting
    var generatedResOutputDir: FileCollection? = null

    @set:VisibleForTesting
    var microApkResDirectory: FileCollection? = null

    @set:VisibleForTesting
    var extraGeneratedResFolders: FileCollection? = null

    var validateEnabled: Boolean = false
        private set

    /**
     * Computes resource sets for merging, if precompileRemoteResources flag is enabled we filter
     * out the non-values and non-layout resources as it's precompiled and is consumed directly in
     * the linking step.
     */
    @JvmOverloads
    fun compute(precompileRemoteResources: Boolean = false): List<ResourceSet> {
        val sourceFolderSets = getResSet()
        var size = sourceFolderSets.size
        libraries?.let {
            size += it.artifacts.size
        }

        val resourceSetList = ArrayList<ResourceSet>(size)

        // add at the beginning since the libraries are less important than the folder based
        // resource sets.
        // get the dependencies first
        libraries?.let {
            val libArtifacts = it.artifacts

            // Layout resources can have databinding values so they need to go through the merging
            // step, same thing for values resources as we impose stricter rules for them different
            // from aapt.
            val folderFilter = { folder: File ->
                folder.name.startsWith(FD_RES_LAYOUT) || folder.name.startsWith(FD_RES_VALUES)
            }

            // the order of the artifact is descending order, so we need to reverse it.
            for (artifact in libArtifacts) {
                val resourceSet = ResourceSet(
                    ProcessApplicationManifest.getArtifactName(artifact),
                    ResourceNamespace.RES_AUTO, null,
                    validateEnabled
                )
                resourceSet.isFromDependency = true
                resourceSet.addSource(artifact.file)

                if (precompileRemoteResources) {
                    resourceSet.setFolderFilter(folderFilter)
                }

                // add to 0 always, since we need to reverse the order.
                resourceSetList.add(0, resourceSet)
            }
        }

        // add the folder based next
        resourceSetList.addAll(sourceFolderSets)

        // We add the generated folders to the main set
        val generatedResFolders = java.util.ArrayList<File>()

        renderscriptResOutputDir?.let {
            generatedResFolders.addAll(it.files)
        }

        generatedResOutputDir?.let {
            generatedResFolders.addAll(it.files)
        }

        extraGeneratedResFolders?.let {
            generatedResFolders.addAll(it.files)
        }
        microApkResDirectory?.let {
            generatedResFolders.addAll(it.files)
        }

        // add the generated files to the main set.
        if (sourceFolderSets.isNotEmpty()) {
            val mainResourceSet = sourceFolderSets[0]
            assert(mainResourceSet.configName == BuilderConstants.MAIN)
            mainResourceSet.addSources(generatedResFolders)
        }

        return resourceSetList
    }

    private fun getResSet(): List<ResourceSet> {
        val builder = ImmutableList.builder<ResourceSet>()
        resources?.let {
            for ((key, value) in it) {
                val resourceSet = ResourceSet(
                    key, ResourceNamespace.RES_AUTO, null, validateEnabled)
                resourceSet.addSources(value.files)
                builder.add(resourceSet)
            }
        }
        return builder.build()
    }

    fun initFromVariantScope(variantScope: VariantScope, includeDependencies: Boolean) {
        val globalScope = variantScope.globalScope
        val variantData = variantScope.variantData
        val project = globalScope.project

        validateEnabled = !globalScope.projectOptions.get(BooleanOption.DISABLE_RESOURCE_VALIDATION)

        if (includeDependencies) {
            libraries =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
        }

        resources = variantData.androidResources

        extraGeneratedResFolders = variantData.extraGeneratedResFolders
        renderscriptResOutputDir = project.files(variantScope.renderscriptResOutputDir)

        generatedResOutputDir = project.files(variantScope.generatedResOutputDir)

        if (variantScope.taskContainer.microApkTask != null &&
            variantData.variantConfiguration.buildType.isEmbedMicroApp) {
            microApkResDirectory = project.files(variantScope.microApkResDirectory)
        }
    }

    fun initForNavigation(variantScope: VariantScope) {
        this.libraries = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
        this.resources = variantScope.variantData.androidResources
    }

    fun getNavigationXmlsList(logger: ILogger): List<File> {
        val resourceSetList = compute()
        val whitelist = Sets.immutableEnumSet(ResourceFolderType.NAVIGATION)
        resourceSetList.forEach {
            it.setResourcesWhitelist(whitelist)
            it.loadFromFiles(logger)
        }
        return resourceSetList.flatMap {
            it.dataMap.asMap().values
                .map { it.first() } // Take the first if there are duplicates?
                .filter { it.type == ResourceType.NAVIGATION }
                .mapNotNull { it.file }
            // reversed in order to have the right precedence (first with the higher priority)
        }.reversed()
    }
}
