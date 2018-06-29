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
@file:JvmName("GradleModelConverterUtil")
package com.android.ide.common.gradle.model

import com.android.builder.model.AaptOptions
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseArtifact
import com.android.builder.model.BaseConfig
import com.android.builder.model.BuildType
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ClassField
import com.android.builder.model.ProductFlavor
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathStrings
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidProject
import com.android.projectmodel.Artifact
import com.android.projectmodel.Config
import com.android.projectmodel.ConfigAssociation
import com.android.projectmodel.ConfigPath
import com.android.projectmodel.ConfigTable
import com.android.projectmodel.ConfigTableSchema
import com.android.projectmodel.DynamicResourceValue
import com.android.projectmodel.ManifestAttributes
import com.android.projectmodel.NamespacingType
import com.android.projectmodel.ProjectType
import com.android.projectmodel.SourceSet
import com.android.projectmodel.Variant
import com.android.projectmodel.matchAllArtifacts
import com.android.projectmodel.matchArtifactsWith
import com.android.projectmodel.matchDimension
import com.android.resources.ResourceType
import com.android.sdklib.AndroidVersion
import com.google.common.collect.ImmutableMap

// This file contains utilities for converting Gradle model types (from builder-model) into project model types.

val ApiVersion.androidVersion: AndroidVersion
    get() = AndroidVersion(apiLevel, codename)

/**
 * Returns the [ProjectType] for the given type ID. Type ids must be one of the values defined by AndroidProject.PROJECT_TYPE_*.
 */
fun getProjectType(typeId: Int): ProjectType {
    return when (typeId) {
        0 -> ProjectType.APP
        1 -> ProjectType.LIBRARY
        2 -> ProjectType.TEST
        4 -> ProjectType.INSTANT_APP
        5 -> ProjectType.FEATURE
        else -> throw IllegalArgumentException("The value $typeId is not a valid project type ID")
    }
}

/**
 * Converts this [IdeAndroidProject] to an [AndroidProject]. The given [cache] determines the scope for de-duping.
 * If the same [ModelCache] is used for multiple conversions, duplicate objects will be merged into the same instance
 * across those conversions. In most situations there will be many duplicate objects within the same project and
 * few duplicate objects between projects, so using the default value will be sufficient.
 */
fun IdeAndroidProject.toProjectModel(cache: ModelCache = ModelCache()): AndroidProject =
    GradleModelConverter(this, cache).convert()

/** Name assigned to the dimension that contains all flavors that aren't explicitly associated with a dimension. */
const val DIM_UNNAMED_FLAVOR = "unnamedFlavorDimension"
/** Name assigned to the dimension that contains build types. */
const val DIM_BUILD_TYPE = "buildType"
/** Name assigned to the dimension that contains artifacts. */
const val DIM_ARTIFACTS = "artifact"

data class VariantContext(val parent: IdeAndroidProject, val variant: IdeVariant)
data class ArtifactContext(val parent: VariantContext, val artifact: IdeBaseArtifact)
data class BuildTypeContext(val buildType: BuildType)
data class FlavorContext(val flavor: ProductFlavor)
data class ConfigTableContext(val parent: IdeAndroidProject)

class GradleModelConverter(
    val project: IdeAndroidProject,
    val cache: ModelCache
) {
    private val schema = getConfigTableSchema(project)

    fun convert(): AndroidProject =
        compute(project) {
            val variants = ArrayList<Variant>()
            forEachVariant {
                variants.add(convert(VariantContext(project, it)))
            }

            AndroidProject(
                name = name,
                type = getProjectType(projectType),
                variants = variants,
                configTable = convert(ConfigTableContext(project))
            )
        }

    fun convert(buildType: BuildTypeContext): Config =
        compute(buildType) {
            val baseValues = getBaseConfig(this.buildType)
            with(this.buildType) {
                baseValues.copy(
                    manifestValues = getManifestAttributes(this),
                    minifyEnabled = isMinifyEnabled
                )
            }
        }

    fun convert(flavor: FlavorContext): Config =
        compute(flavor) {
            val baseValues = getBaseConfig(this.flavor)
            with(this.flavor) {
                baseValues.copy(
                    manifestValues = getManifestAttributes(this),
                    testInstrumentationRunner = testInstrumentationRunner,
                    testInstrumentationRunnerArguments = testInstrumentationRunnerArguments,
                    resourceConfigurations = resourceConfigurations,
                    usingSupportLibVectors = vectorDrawables.useSupportLibrary == true
                )
            }
        }

    private inline fun forEachArtifact(variant: IdeVariant, block: (ConfigPath, BaseArtifact) -> Unit) {
        block(schema.matchArtifact(ARTIFACT_NAME_MAIN), variant.mainArtifact)
        variant.extraAndroidArtifacts.forEach {
            block(schema.matchArtifact(it.name), it)
        }
        variant.extraJavaArtifacts.forEach {
            block(schema.matchArtifact(it.name), it)
        }
    }

    fun convert(configTable: ConfigTableContext): ConfigTable =
        compute(configTable) {
            with(parent) {
                // Set up the config table
                val configSchema = getConfigTableSchema(this)
                val configs = ArrayList<ConfigAssociation>()

                // Add the main config
                configs.addAll(configsFor(matchAllArtifacts(), defaultConfig))

                // Add the flavor configs
                productFlavors.forEach {
                    configs.addAll(configsFor(configSchema.pathFor(it.productFlavor.name), it))
                }

                // Add the multi-flavor configs
                val multiFlavorConfigs = HashMap<ConfigPath, Config>()
                forEachVariant {
                    val multiFlavorPath = matchArtifactsWith(it.productFlavors)
                    forEachArtifact(it) { path, artifact ->
                        val sourceProvider = artifact.multiFlavorSourceProvider
                        if (sourceProvider != null) {
                            val artifactPath = multiFlavorPath.intersect(path)
                            multiFlavorConfigs.getOrPut(artifactPath) {
                                val cfg = Config(sources = convert(sourceProvider))
                                configs.add(ConfigAssociation(artifactPath, cfg))
                                cfg
                            }
                        }
                    }
                }

                // Add the build types
                buildTypes.forEach {
                    configs.addAll(configsFor(it))
                }

                // Add the per-variant configs
                forEachVariant {
                    val variantPath = matchArtifactsForVariant(it)
                    forEachArtifact(it) { path, artifact ->
                        val sourceProvider = artifact.variantSourceProvider
                        if (sourceProvider != null) {
                            val artifactPath = variantPath.intersect(path)
                            configs.add(ConfigAssociation(artifactPath, Config(sources = convert(sourceProvider))))
                        }
                    }
                }

                ConfigTable(
                    schema = configSchema,
                    associations = configs
                )
            }
        }

    fun convert(sourceProvider: SourceProvider): SourceSet =
        compute(sourceProvider) {
            toSourceSet()
        }

    fun convert(variant: VariantContext): Variant =
        compute(variant) {
            with(variant.variant) {
                val androidTestArtifact = androidTestArtifact
                Variant(
                    name = name,
                    displayName = displayName,
                    mainArtifact = convert(ArtifactContext(variant, mainArtifact)),
                    androidTestArtifact = androidTestArtifact?.let { convert(ArtifactContext(variant, it)) },
                    unitTestArtifact = unitTestArtifact?.let { convert(ArtifactContext(variant, it)) },
                    extraArtifacts = extraAndroidArtifacts
                        .filter { it != mainArtifact && it != androidTestArtifact }
                        .mapNotNull { it as? IdeBaseArtifact }
                        .map { convert(ArtifactContext(variant, it)) },
                    extraJavaArtifacts = extraJavaArtifacts
                        .filter { it != unitTestArtifact }
                        .mapNotNull { it as? IdeBaseArtifact }
                        .map { convert(ArtifactContext(variant, it)) },
                    configPath = matchArtifactsForVariant(this)
                )
            }
        }

    /**
     * Converts a builder-model's Artifact into a project model [Artifact].
     */
    fun convert(artifact: ArtifactContext): Artifact =
        compute(artifact) {
            with(artifact.artifact) {
                val artifactName = if (this == artifact.parent.variant.mainArtifact) ARTIFACT_NAME_MAIN else name
                val configTable = convert(ConfigTableContext(artifact.parent.parent))
                val variantPath = matchArtifactsForVariant(artifact.parent.variant)
                val artifactPath = variantPath.intersect(configTable.schema.matchArtifact(artifactName))

                // Compute the resolved configuration for this artifact. There's two ways to compute the resolved configuration:
                // 1. Iterate over the constituent configs in the config table and merge them all.
                // 2. Make use of the "mergedFlavor" attribute provided by Gradle.
                //
                // Approach 1 is simpler since it would just be a trivial for loop, but it assumes the IDE's merge logic is exactly the same
                // as Gradle's. Approach 2 is preferred since - if gradle adds any special cases to its merge algorithm - it would report those
                // special cases as part of mergedFlavor and we'd automatically take them into account. Unfortunately, this approach is also
                // a lot more complicated due to special cases in builder-model.
                // - The mergedFlavor structure does not include a source provider, so we need to compute the source inclusions manually
                //   using approach 1.
                // - The mergedFlavor structure does not include config information from the build type or any variant-specific overloads.
                //
                // So the algorithm is: first compute the source inclusions corresponding to the mergedFlavor configuration (that's the main
                // source configurations along with any flavor-specific inclusions, minus any variant-specific inclusions). Then convert
                // mergedFlavor to a Config and attach those source inclusions. Then do a manual merge of that config with the build type and
                // any variant-specific Configs. Finally, we override any metadata (like the application ID) that Gradle has attached directly
                // to the artifact. Even if that disagrees with the merged values we computed via the algorithm above, we always prefer any
                // information supplied directly by Gradle.

                val associationsToProcess = ArrayList<ConfigAssociation>()

                // First, compute the sources for the global config or any config that is flavor-specific.
                var mergedSource = SourceSet()
                for (config in configTable.associations) {
                    // Skip configs that don't apply to this artifact
                    if (!config.path.intersects(artifactPath)) {
                        continue
                    }

                    // If this is something that would be included in the "merged flavor", include its sources here.
                    if ((matchesAllVariants(config.path) || !matchesAllFlavors(config.path)) && !isVariantSpecific(config.path)) {
                        mergedSource += config.config.sources
                    }
                    else {
                        associationsToProcess.add(config)
                    }
                }

                // Compute the merged flavor configuration. This won't include any sources that came from source sets, so
                // we merge it with the merged sources we computed, above
                val flavorCombinationConfig = convert(FlavorContext(artifact.parent.variant.mergedFlavor))
                // Attach the manually-computed source providers
                var mergedConfig = flavorCombinationConfig.copy(sources = mergedSource + flavorCombinationConfig.sources)

                // Merge the additional configurations with the merged flavor. This will apply information about the build type and
                // variant-specific overrides.
                for (config in associationsToProcess) {
                    mergedConfig = mergedConfig.mergeWith(config.config)
                }

                if (this is AndroidArtifact) {
                    mergedConfig = mergedConfig.copy(
                        manifestValues = mergedConfig.manifestValues.copy(
                            applicationId = applicationId
                        ),
                        resValues = mergedConfig.resValues + classFieldsToDynamicResourceValues(resValues)
                    )
                }

                Artifact(
                    name = artifactName,
                    classFolders = listOf(PathString(classesFolder)) + additionalClassesFolders.toPathStrings(),
                    resolved = mergedConfig
                )
            }
        }

    /**
     * Returns true if the given path only applies to one variant
     */
    private fun isVariantSpecific(path: ConfigPath): Boolean {
        val segments = path.segments ?: return false

        val firstNull = segments.indexOfFirst { it == null }
        return (firstNull == -1 || firstNull >= schema.dimensions.count() - 2)
    }

    private fun matchesAllFlavors(path: ConfigPath) =
        matchesAllInDimension(path, schema.dimensions.count() - 2)

    private fun matchesAllVariants(path: ConfigPath) =
        matchesAllInDimension(path, schema.dimensions.count() - 1)

    /**
     * Returns true iff the given patch matches everything in the first n dimensions.
     */
    private fun matchesAllInDimension(path: ConfigPath, dim: Int): Boolean {
        val segments = path.segments ?: return false
        // The first dim-1 segments of the path correspond to the variant name. If there are no non-null values in any
        // of these segments then the path applies to all variants.
        val firstNonNull = segments.indexOfFirst { it != null }
        if (firstNonNull == -1) {
            return true
        }
        return firstNonNull >= dim
    }

    private fun matchBuildType(buildType: String) =
        matchDimension(schema.dimensions.size - 2, buildType)

    private fun matchArtifactsForVariant(variant: IdeVariant): ConfigPath =
        matchArtifactsWith(variant.productFlavors + variant.buildType)

    /**
     * Returns the list of [ConfigAssociation] for a [ProductFlavorContainer], given an [artifactFilter] that identifies
     * which artifacts the [ProductFlavorContainer] should apply to. Note that Gradle uses the [ProductFlavorContainer]
     * struct for more than just flavor-specific information. It is also used for the main config (which applies to
     * all variants). For this reason, it is the responsibility of the caller to pass in an [artifactFilter] that
     * tells this method what the given [flavor] is really describing.
     */
    private fun configsFor(artifactFilter: ConfigPath, flavor: ProductFlavorContainer): List<ConfigAssociation> {
        val result = ArrayList<ConfigAssociation>()

        // This config stores the base metadata about the flavor, without the paths from the source providers.
        val configWithoutSourceProvider = convert(FlavorContext(flavor.productFlavor))

        result.add(
            // The ConfigPath for the main configuration is a path that matches both the main artifact and the current variant (if any).
            // The sources are the (probably empty) set of sources from the flavor metadata itself combined with the sources from
            // the flavor's source provider.
            ConfigAssociation(
                artifactFilter.intersect(schema.matchArtifact(ARTIFACT_NAME_MAIN)),
                configWithoutSourceProvider.copy(sources = configWithoutSourceProvider.sources + convert(flavor.sourceProvider))
            )
        )
        for (next in flavor.extraSourceProviders) {
            result.add(
                ConfigAssociation(
                    artifactFilter.intersect(schema.matchArtifact(next.artifactName)),
                    configWithoutSourceProvider.copy(sources = configWithoutSourceProvider.sources + convert(next.sourceProvider))
                )
            )
        }

        return result
    }

    /**
     * Returns the list of [ConfigAssociation] for the given [BuildTypeContainer].
     */
    private fun configsFor(buildType: BuildTypeContainer): List<ConfigAssociation> {
        val artifactFilter = matchBuildType(buildType.buildType.name)
        val result = ArrayList<ConfigAssociation>()
        val configWithoutSources = convert(BuildTypeContext(buildType.buildType))

        result.add(
            // The ConfigPath for the main configuration is a path that matches both the main artifact and the current variant (if any).
            ConfigAssociation(
                artifactFilter.intersect(schema.matchArtifact(ARTIFACT_NAME_MAIN)),
                configWithoutSources.copy(sources = configWithoutSources.sources + convert(buildType.sourceProvider))
            )
        )
        for (next in buildType.extraSourceProviders) {
            result.add(
                ConfigAssociation(
                    artifactFilter.intersect(schema.matchArtifact(next.artifactName)),
                    configWithoutSources.copy(sources = configWithoutSources.sources + convert(next.sourceProvider))
                )
            )
        }

        return result
    }

    /**
     * Computes the [ConfigTableSchema] for the given project.
     */
    private fun getConfigTableSchema(input: IdeAndroidProject): ConfigTableSchema {
        val builder = ConfigTableSchema.Builder()
        with(input) {
            flavorDimensions.forEach {
                builder.getOrPutDimension(it)
            }
            productFlavors.forEach {
                builder.getOrPutDimension(it.productFlavor.dimension ?: DIM_UNNAMED_FLAVOR).add(it.productFlavor.name)
            }
            val buildTypeDimension = builder.getOrPutDimension(DIM_BUILD_TYPE)
            buildTypes.forEach {
                buildTypeDimension.add(it.buildType.name)
            }
            val artifactDimension = builder.getOrPutDimension(DIM_ARTIFACTS)
            artifactDimension.add(ARTIFACT_NAME_MAIN)
            forEachVariant {
                it.extraAndroidArtifacts.forEach {
                    artifactDimension.add(it.name)
                }
                it.extraJavaArtifacts.forEach {
                    artifactDimension.add(it.name)
                }
            }
        }

        return builder.build()
    }

    private fun getBaseConfig(config: BaseConfig): Config {
        with(config) {
            val sources = HashMap<AndroidPathType, List<PathString>>()
            sources[AndroidPathType.PROGUARD_FILE] = proguardFiles.toPathStrings()
            sources[AndroidPathType.CONSUMER_PROGUARD_FILE] = consumerProguardFiles.toPathStrings()

            return Config(
                applicationIdSuffix = applicationIdSuffix,
                versionNameSuffix = versionNameSuffix,
                manifestPlaceholderValues = manifestPlaceholders,
                sources = SourceSet(sources),
                resValues = classFieldsToDynamicResourceValues(resValues)
            )
        }
    }

    private fun getManifestAttributes(flavor: ProductFlavor): ManifestAttributes {
        with(flavor) {
            return ManifestAttributes(
                applicationId = applicationId,
                versionCode = versionCode,
                versionName = versionName,
                minSdkVersion = minSdkVersion?.androidVersion,
                maxSdkVersion = maxSdkVersion?.let { AndroidVersion(it) },
                targetSdkVersion = targetSdkVersion?.androidVersion
            )
        }
    }

    private fun getManifestAttributes(buildType: BuildType): ManifestAttributes {
        return ManifestAttributes(
            debuggable = buildType.isDebuggable
        )
    }

    private fun <K, V> compute(key: K, lambda: K.() -> V): V {
        return cache.computeIfAbsent(key, { key.lambda() })
    }
}

/**
 * Convert a [NamespacingType] to an [AaptOptions.Namespacing].
 */
fun NamespacingType.toAaptOptionsNamespacing(): AaptOptions.Namespacing = when (this) {
    NamespacingType.DISABLED -> AaptOptions.Namespacing.DISABLED
    NamespacingType.REQUIRED -> AaptOptions.Namespacing.REQUIRED
}

/**
 * Converts this [SourceProvider] to a [SourceSet].
 */
fun SourceProvider.toSourceSet() = SourceSet(
    mapOf(
        AndroidPathType.MANIFEST to listOf(PathString(manifestFile)),
        AndroidPathType.JAVA to javaDirectories.toPathStrings(),
        AndroidPathType.RESOURCE to resourcesDirectories.toPathStrings(),
        AndroidPathType.AIDL to aidlDirectories.toPathStrings(),
        AndroidPathType.RENDERSCRIPT to renderscriptDirectories.toPathStrings(),
        AndroidPathType.C to cDirectories.toPathStrings(),
        AndroidPathType.CPP to cppDirectories.toPathStrings(),
        AndroidPathType.RES to resDirectories.toPathStrings(),
        AndroidPathType.ASSETS to assetsDirectories.toPathStrings(),
        AndroidPathType.JNI_LIBS to jniLibsDirectories.toPathStrings(),
        AndroidPathType.SHADERS to shadersDirectories.toPathStrings()
    )
)

fun classFieldsToDynamicResourceValues(classFields: Map<String, ClassField>): Map<String, DynamicResourceValue> {
    val result = HashMap<String, DynamicResourceValue>()
    for (field in classFields.values) {
        val resourceType = ResourceType.fromClassName(field.type)
        if (resourceType != null) {
            result[field.name] = DynamicResourceValue(resourceType, field.value)
        }
    }
    return ImmutableMap.copyOf(result)
}
