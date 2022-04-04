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

package com.android.build.api.component.impl

import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.DirectoryEntry
import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.compiling.BuildConfigType
import com.android.builder.model.SourceProvider
import java.util.Collections
import java.io.File

/**
 * Computes the default sources for all [com.android.build.api.variant.impl.SourceType]s.
 */
class DefaultSourcesProviderImpl(
    val component: ComponentCreationConfig,
    val variantSources: VariantSources,
): DefaultSourcesProvider {

    override val java: List<DirectoryEntry>
        get() = component.defaultJavaSources()

    override val kotlin: List<DirectoryEntry>
        get() {
            val sourceSets = mutableListOf<DirectoryEntry>()
            for (sourceProvider in variantSources.sortedSourceProviders) {
                val sourceSet = sourceProvider as AndroidSourceSet
                for (srcDir in (sourceSet.kotlin as DefaultAndroidSourceDirectorySet).srcDirs) {
                    sourceSets.add(
                        FileBasedDirectoryEntryImpl(
                            name = sourceSet.name,
                            directory = srcDir,
                            filter = null,
                        )
                    )
                }
            }
            return sourceSets
        }

    override val res: List<DirectoryEntries>
        get() = component.defaultResSources()
    override val assets: List<DirectoryEntries>
        get() = defaultAssetsSources()

    override val jniLibs: List<DirectoryEntries>
        get() = getSourceList {
                sourceProvider -> sourceProvider.jniLibsDirectories
        }

    override val shaders: List<DirectoryEntries>?
        get() = if (component.buildFeatures.shaders) getSourceList {
                sourceProvider -> sourceProvider.shadersDirectories
        } else null

    override val aidl: List<DirectoryEntry>?
        get() = if (component.buildFeatures.aidl) {
            flattenSourceProviders { sourceSet -> sourceSet.aidl }
        } else null

    override val mlModels: List<DirectoryEntries>
        get() = getSourceList { sourceProvider ->
            sourceProvider.mlModelsDirectories
        }

    override val renderscript: List<DirectoryEntry>?
        get() = if (component.buildFeatures.renderScript) {
            flattenSourceProviders { sourceSet -> sourceSet.renderscript }
        } else null

    private fun flattenSourceProviders(
        sourceDirectory: (sourceSet: AndroidSourceSet) -> AndroidSourceDirectorySet
    ): List<DirectoryEntry> {
        val sourceSets = mutableListOf<DirectoryEntry>()
        for (sourceProvider in variantSources.sortedSourceProviders) {
            val sourceSet = sourceProvider as AndroidSourceSet
            for (srcDir in sourceDirectory(sourceSet).srcDirs) {
                sourceSets.add(
                    FileBasedDirectoryEntryImpl(
                        name = sourceSet.name,
                        directory = srcDir,
                        filter = sourceDirectory(sourceSet).filter,
                    )
                )
            }
        }
        return sourceSets
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     * For access to the final list of java sources, use [com.android.build.api.variant.Sources]
     *
     * Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    private fun ComponentCreationConfig.defaultJavaSources(): List<DirectoryEntry> {
        // Build the list of source folders.
        val sourceSets = mutableListOf<DirectoryEntry>()

        // First the actual source folders.
        sourceSets.addAll(
            flattenSourceProviders { sourceSet -> sourceSet.java }
        )

        // for the other, there's no duplicate so no issue.
        if (buildConfigCreationConfig?.buildConfigType == BuildConfigType.JAVA_SOURCE) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_build_config",
                    artifacts.get(InternalArtifactType.GENERATED_BUILD_CONFIG_JAVA),
                )
            )
        }
        if (taskContainer.aidlCompileTask != null) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    "generated_aidl",
                    artifacts.get(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR),
                )
            )
        }
        if (buildFeatures.dataBinding || buildFeatures.viewBinding) {
            // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
            if (buildFeatures.dataBinding) {
                // Under some conditions (e.g., for a unit test variant where
                // includeAndroidResources == false or testedVariantType != AAR, see
                // TaskManager.createUnitTestVariantTasks), the artifact may not have been created,
                // so we need to check its presence first (using internal AGP API instead of Gradle
                // API---see https://android.googlesource.com/platform/tools/base/+/ca24108e58e6e0dc56ce6c6f639cdbd0fa3b812f).
                if (!artifacts.getArtifactContainer(InternalArtifactType.DATA_BINDING_TRIGGER)
                        .needInitialProducer().get()
                ) {
                    sourceSets.add(
                        TaskProviderBasedDirectoryEntryImpl(
                            name = "databinding_generated",
                            directoryProvider = artifacts.get(InternalArtifactType.DATA_BINDING_TRIGGER),
                        )
                    )
                }
            }
            addDataBindingSources(sourceSets)
        }
        (component as? ConsumableCreationConfig)
            ?.renderscriptCreationConfig
            ?.addRenderscriptSources(sourceSets)
        if (buildFeatures.mlModelBinding) {
            sourceSets.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "mlModel_generated",
                    directoryProvider = artifacts.get(InternalArtifactType.ML_SOURCE_OUT),
                )
            )
        }
        return sourceSets
    }

    private fun ComponentCreationConfig.defaultResSources(): List<DirectoryEntries> {
        val sourceDirectories = mutableListOf<DirectoryEntries>()

        sourceDirectories.addAll(
            getSourceList { sourceProvider -> sourceProvider.resDirectories }
        )

        val generatedFolders = mutableListOf<DirectoryEntry>()
        if (buildFeatures.renderScript) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "renderscript_generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES),
                )
            )
        }

        if (buildFeatures.resValues) {
            generatedFolders.add(
                TaskProviderBasedDirectoryEntryImpl(
                    name = "generated_res",
                    directoryProvider = artifacts.get(InternalArtifactType.GENERATED_RES),
                )
            )
        }

        sourceDirectories.add(DirectoryEntries("generated", generatedFolders))

        return Collections.unmodifiableList(sourceDirectories)
    }

    private fun defaultAssetsSources(): List<DirectoryEntries> =
        getSourceList { sourceProvider -> sourceProvider.assetsDirectories }

    private fun getSourceList(action: (sourceProvider: SourceProvider) -> Collection<File>): List<DirectoryEntries> {
        return variantSources.sortedSourceProviders.map { sourceProvider ->
            DirectoryEntries(
                sourceProvider.name,
                action(sourceProvider).map { directory ->
                    FileBasedDirectoryEntryImpl(
                        sourceProvider.name,
                        directory,
                    )
                }
            )
        }
    }
}
