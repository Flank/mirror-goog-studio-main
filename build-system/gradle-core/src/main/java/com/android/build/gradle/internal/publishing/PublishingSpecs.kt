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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.BUNDLE_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.scope.TaskOutputHolder.AnchorOutputType.ALL_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.AIDL_PARCELABLE
import com.android.build.gradle.internal.scope.InternalArtifactType.APK
import com.android.build.gradle.internal.scope.InternalArtifactType.APK_MAPPING
import com.android.build.gradle.internal.scope.InternalArtifactType.APP_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.CONSUMER_PROGUARD_FILE
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_APPLICATION_ID_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_IDS_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_TRANSITIVE_DEPS
import com.android.build.gradle.internal.scope.InternalArtifactType.FULL_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_JNI
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RES_FOR_BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.METADADA_FEATURE_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_APP_ID_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.METADATA_FEATURE_DECLARATION
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLIC_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_DEX
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_HEADERS
import com.android.build.gradle.internal.scope.InternalArtifactType.RES_STATIC_LIBRARY
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
import com.android.build.gradle.internal.scope.InternalArtifactType.DEFINED_ONLY_SYMBOL_LIST
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.builder.core.VariantType
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps

/**
 * Publishing spec for variants and tasks outputs.
 *
 *
 * This builds a bi-directional mapping between task outputs and published artifacts (for project
 * to project publication), as well as where to publish the artifact (with
 * [org.gradle.api.artifacts.Configuration] via the [PublishedConfigType] enum.)
 *
 *
 * This mapping is per [VariantType] to allow for different task outputs to be published
 * under the same [ArtifactType].
 *
 *
 * This mapping also offers reverse mapping override for tests (per [VariantType] as well),
 * allowing a test variant to not use exactly the published artifact of the tested variant but a
 * different version. This allows for instance the unit tests of libraries to use the full Java
 * classes, including the R class for unit testing, while the published artifact does not contain
 * the R class. Similarly, the override can extend the published scope (api vs runtime), which is
 * needed to run the unit tests.
 */
class PublishingSpecs {

    /**
     * The publishing spec for a variant
     */
    interface VariantSpec {
        val variantType: VariantType
        val outputs: Set<OutputSpec>
        val testingSpecs: Map<VariantType, VariantSpec>

        fun getTestingSpec(variantType: VariantType): VariantSpec

        fun getSpec(artifactType: ArtifactType): OutputSpec?

        fun getSpec(taskOutputType: com.android.build.api.artifact.ArtifactType): OutputSpec?
    }

    /**
     * A published output
     */
    interface OutputSpec {
        val outputType: com.android.build.api.artifact.ArtifactType
        val artifactType: ArtifactType
        val publishedConfigTypes: ImmutableList<PublishedConfigType>
    }

    companion object {
        private val variantMap = Maps.newEnumMap<VariantType, VariantSpec>(VariantType::class.java)

        init {
            variantSpec(VariantType.APK) {

                api(MANIFEST_METADATA, ArtifactType.MANIFEST_METADATA)
                // use TYPE_JAR to give access to this via the model for now,
                // the JarTransform will convert it back to CLASSES
                // FIXME: stop using TYPE_JAR for APK_CLASSES
                api(APP_CLASSES, ArtifactType.JAR)
                api(APK_MAPPING, ArtifactType.APK_MAPPING)

                runtime(APK, ArtifactType.APK)

                metadata(METADATA_APP_ID_DECLARATION, ArtifactType.METADATA_APP_ID_DECLARATION)

                bundle(MERGED_ASSETS, ArtifactType.ASSETS)
                bundle(PUBLISHED_DEX, ArtifactType.DEX)
                bundle(PUBLISHED_JAVA_RES, ArtifactType.JAVA_RES)
                bundle(PUBLISHED_NATIVE_LIBS, ArtifactType.JNI)
                bundle(LINKED_RES_FOR_BUNDLE, ArtifactType.RES_BUNDLE)

                testSpec(VariantType.ANDROID_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping
                    api(APP_CLASSES, ArtifactType.CLASSES)
                }

                testSpec(VariantType.UNIT_TEST) {
                    // java output query is done via CLASSES instead of JAR, so provide
                    // the right backward mapping. Also add it to the runtime as it's
                    // needed to run the tests!
                    output(ALL_CLASSES, ArtifactType.CLASSES)
                    // JAVA_RES isn't published by the app, but we need it for the unit tests
                    output(JAVA_RES, ArtifactType.JAVA_RES)
                }
            }

            variantSpec(VariantType.LIBRARY) {
                api(COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                        ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                api(AIDL_PARCELABLE, ArtifactType.AIDL)
                api(RENDERSCRIPT_HEADERS, ArtifactType.RENDERSCRIPT)

                // manifest is published to both to compare and detect provided-only library
                // dependencies.
                output(LIBRARY_MANIFEST, ArtifactType.MANIFEST)
                output(RES_STATIC_LIBRARY, ArtifactType.RES_STATIC_LIBRARY)
                output(DATA_BINDING_ARTIFACT, ArtifactType.DATA_BINDING_ARTIFACT)
                output(DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
                        ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT)
                output(LIBRARY_CLASSES, ArtifactType.CLASSES)
                output(FULL_JAR, ArtifactType.JAR)

                runtime(LIBRARY_ASSETS, ArtifactType.ASSETS)
                runtime(PACKAGED_RES, ArtifactType.ANDROID_RES)
                runtime(PUBLIC_RES, ArtifactType.PUBLIC_RES)
                runtime(SYMBOL_LIST, ArtifactType.SYMBOL_LIST)
                runtime(SYMBOL_LIST_WITH_PACKAGE_NAME, ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
                runtime(DEFINED_ONLY_SYMBOL_LIST, ArtifactType.DEFINED_ONLY_SYMBOL_LIST)
                runtime(LIBRARY_JAVA_RES, ArtifactType.JAVA_RES)
                runtime(CONSUMER_PROGUARD_FILE, ArtifactType.PROGUARD_RULES)
                runtime(LIBRARY_JNI, ArtifactType.JNI)
                runtime(LINT_JAR, ArtifactType.LINT)

                testSpec(VariantType.UNIT_TEST) {
                    // unit test need ALL_CLASSES instead of LIBRARY_CLASSES to get
                    // access to the R class. Also scope should be API+Runtime.
                    output(ALL_CLASSES, ArtifactType.CLASSES)
                }
            }

            variantSpec(VariantType.FEATURE) {
                metadata(METADATA_FEATURE_DECLARATION, ArtifactType.METADATA_FEATURE_DECLARATION)
                metadata(METADADA_FEATURE_MANIFEST, ArtifactType.METADATA_FEATURE_MANIFEST)

                api(FEATURE_IDS_DECLARATION, ArtifactType.FEATURE_IDS_DECLARATION)
                api(FEATURE_APPLICATION_ID_DECLARATION,
                        ArtifactType.FEATURE_APPLICATION_ID_DECLARATION)
                api(FEATURE_RESOURCE_PKG, ArtifactType.FEATURE_RESOURCE_PKG)
                api(FEATURE_CLASSES, ArtifactType.CLASSES)

                runtime(FEATURE_TRANSITIVE_DEPS, ArtifactType.FEATURE_TRANSITIVE_DEPS)
                runtime(APK, ArtifactType.APK)
            }


            // empty specs
            variantSpec(VariantType.ANDROID_TEST)
            variantSpec(VariantType.UNIT_TEST)
            variantSpec(VariantType.INSTANTAPP)
        }

        @JvmStatic
        fun getVariantSpec(variantType: VariantType): VariantSpec {
            return variantMap[variantType]!!
        }

        @JvmStatic
        internal fun getVariantMap(): Map<VariantType, VariantSpec> {
            return variantMap
        }

        private fun variantSpec(
                variantType: VariantType,
                action: VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit) {
            val specBuilder = VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                    variantType)
            action(specBuilder)
            variantMap[variantType] = specBuilder.toSpec()
        }

        private fun variantSpec(variantType: VariantType) {
            variantMap[variantType] = VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                    variantType).toSpec()
        }
    }

    interface VariantSpecBuilder<in T : com.android.build.api.artifact.ArtifactType> {
        val variantType: VariantType

        fun output(taskOutputType: T, action: OutputSpecBuilder.() -> Unit)
        fun output(taskOutputType: T, artifactType: ArtifactType)
        fun api(taskOutputType: T, artifactType: ArtifactType)
        fun runtime(taskOutputType: T, artifactType: ArtifactType)
        fun metadata(taskOutputType: T, artifactType: ArtifactType)
        fun bundle(taskOutputType: T, artifactType: ArtifactType)

        fun testSpec(variantType: VariantType, action: VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit)
    }

    interface OutputSpecBuilder: OutputSpec {
        override var artifactType: ArtifactType
        override var publishedConfigTypes: ImmutableList<PublishedConfigType>
    }

}

private val API_ELEMENTS_ONLY = ImmutableList.of(API_ELEMENTS)
private val RUNTIME_ELEMENTS_ONLY = ImmutableList.of(RUNTIME_ELEMENTS)
private val API_AND_RUNTIME_ELEMENTS = ImmutableList.of(API_ELEMENTS, RUNTIME_ELEMENTS)
private val METADATA_ELEMENTS_ONLY = ImmutableList.of(METADATA_ELEMENTS)
private val BUNDLE_ELEMENTS_ONLY = ImmutableList.of(BUNDLE_ELEMENTS)

// --- Implementation of the public Spec interfaces

private class VariantPublishingSpecImpl(
        override val variantType: VariantType,
        private val parentSpec: PublishingSpecs.VariantSpec?,
        override val outputs: Set<PublishingSpecs.OutputSpec>,
        testingSpecBuilders: Map<VariantType, VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>>
) : PublishingSpecs.VariantSpec {

    override val testingSpecs: Map<VariantType, PublishingSpecs.VariantSpec>
    private var _artifactMap: Map<ArtifactType, PublishingSpecs.OutputSpec>? = null
    private var _outputMap: Map<com.android.build.api.artifact.ArtifactType, PublishingSpecs.OutputSpec>? = null

    private val artifactMap: Map<ArtifactType, PublishingSpecs.OutputSpec>
        get() {
            val map = _artifactMap
            return if (map == null) {
                val map2 = outputs.associate { it.artifactType to it }
                _artifactMap = map2
                map2
            } else {
                map
            }
        }

    private val outputMap: Map<com.android.build.api.artifact.ArtifactType, PublishingSpecs.OutputSpec>
        get() {
            val map = _outputMap
            return if (map == null) {
                val map2 = outputs.associate { it.outputType to it }
                _outputMap = map2
                map2
            } else {
                map
            }
        }


    init {
        testingSpecs = testingSpecBuilders.toImmutableMap { it.toSpec(this) }
    }

    override fun getTestingSpec(variantType: VariantType): PublishingSpecs.VariantSpec {
        Preconditions.checkState(variantType.isForTesting)

        val testingSpec = testingSpecs[variantType]
        return testingSpec ?: this
    }

    override fun getSpec(artifactType: ArtifactType): PublishingSpecs.OutputSpec? {
        val spec = artifactMap[artifactType]
        return spec ?: parentSpec?.getSpec(artifactType)
    }

    override fun getSpec(taskOutputType: com.android.build.api.artifact.ArtifactType): PublishingSpecs.OutputSpec? {
        val spec = outputMap[taskOutputType]
        return spec ?: parentSpec?.getSpec(taskOutputType)
    }
}

private data class OutputSpecImpl(
        override val outputType: com.android.build.api.artifact.ArtifactType,
        override val artifactType: ArtifactType,
        override val publishedConfigTypes: ImmutableList<PublishedConfigType>) : PublishingSpecs.OutputSpec

// -- Implementation of the internal Spec Builder interfaces

private class VariantSpecBuilderImpl<in T : com.android.build.api.artifact.ArtifactType>(
        override val variantType: VariantType): PublishingSpecs.VariantSpecBuilder<T> {

    private val outputs = mutableSetOf<PublishingSpecs.OutputSpec>()
    private val testingSpecs = mutableMapOf<VariantType, VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>>()

    override fun output(
            taskOutputType: T,
            action: (PublishingSpecs.OutputSpecBuilder) -> Unit) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        action(specBuilder)

        outputs.add(specBuilder.toSpec())
    }

    override fun output(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        outputs.add(specBuilder.toSpec())
    }

    override fun api(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = API_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun runtime(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = RUNTIME_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun metadata(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = METADATA_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun bundle(taskOutputType: T, artifactType: ArtifactType) {
        val specBuilder = OutputSpecBuilderImpl(
                taskOutputType)
        specBuilder.artifactType = artifactType
        specBuilder.publishedConfigTypes = BUNDLE_ELEMENTS_ONLY
        outputs.add(specBuilder.toSpec())
    }

    override fun testSpec(
            variantType: VariantType,
            action: PublishingSpecs.VariantSpecBuilder<com.android.build.api.artifact.ArtifactType>.() -> Unit) {
        Preconditions.checkState(!this.variantType.isForTesting)
        Preconditions.checkState(variantType.isForTesting)
        Preconditions.checkState(!testingSpecs.containsKey(variantType))

        val specBuilder = VariantSpecBuilderImpl<com.android.build.api.artifact.ArtifactType>(
                variantType)
        action(specBuilder)

        testingSpecs[variantType] = specBuilder
    }

    fun toSpec(parentSpec: PublishingSpecs.VariantSpec? = null): PublishingSpecs.VariantSpec {
        return VariantPublishingSpecImpl(
                variantType,
                parentSpec,
                outputs.toImmutableSet(),
                testingSpecs)
    }
}

private class OutputSpecBuilderImpl(override val outputType: com.android.build.api.artifact.ArtifactType) : PublishingSpecs.OutputSpecBuilder {
    override lateinit var artifactType: ArtifactType
    override var publishedConfigTypes: ImmutableList<PublishedConfigType> = API_AND_RUNTIME_ELEMENTS

    fun toSpec(): PublishingSpecs.OutputSpec = OutputSpecImpl(
            outputType,
            artifactType,
            publishedConfigTypes)
}
