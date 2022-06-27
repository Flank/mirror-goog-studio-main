/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.ComposeOptions
import com.android.build.api.dsl.DataBinding
import com.android.build.api.dsl.ExternalNativeBuild
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.dsl.PrefabPackagingOptions
import com.android.build.api.dsl.Splits
import com.android.build.api.dsl.TestCoverage
import com.android.build.api.dsl.TestOptions
import com.android.build.api.transform.Transform
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.dsl.LanguageSplitOptions
import com.android.build.gradle.internal.instrumentation.ASM_API_VERSION_FOR_INSTRUMENTATION
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BaseServices
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.LibraryRequest
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.repository.Revision
import com.android.utils.HelpfulEnumConverter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

class GlobalTaskCreationConfigImpl(
    project: Project,
    private val oldExtension: BaseExtension,
    private val extension: CommonExtensionImpl<*, *, *, *>,
    override val services: BaseServices,
    private val versionedSdkLoaderService: VersionedSdkLoaderService,
    bootClasspathConfig: BootClasspathConfigImpl,
    override val lintPublish: Configuration,
    override val lintChecks: Configuration,
    private val androidJar: Configuration
) : GlobalTaskCreationConfig, BootClasspathConfig by bootClasspathConfig {

    companion object {
        @JvmStatic
        fun String.toExecutionEnum(): com.android.builder.model.TestOptions.Execution? {
            val converter = HelpfulEnumConverter(
                com.android.builder.model.TestOptions.Execution::class.java
            )
            return converter.convert(this)
        }
    }

    init {
        bootClasspathConfig.androidJar = androidJar
    }

    // DSL elements

    override val compileSdkHashString: String
        get() = extension.compileSdkVersion ?: throw RuntimeException("compileSdk is not specified!")

    override val buildToolsRevision: Revision by lazy {
        Revision.parseRevision(extension.buildToolsVersion, Revision.Precision.MICRO)
    }

    override val ndkVersion: String?
        get() = extension.ndkVersion

    override val ndkPath: String?
        get() = extension.ndkPath

    override val productFlavorCount: Int
        get() = extension.productFlavors.size

    override val productFlavorDimensionCount: Int
        get() = extension.flavorDimensions.size

    override val assetPacks: Set<String>
        get() = (extension as? ApplicationExtension)?.assetPacks ?: setOf()

    override val dynamicFeatures: Set<String>
        get() = (extension as? ApplicationExtension)?.dynamicFeatures ?: setOf()

    override val hasDynamicFeatures: Boolean
        get() = dynamicFeatures.isNotEmpty()

    override val aidlPackagedList: Collection<String>?
        get() {
            val libExt = (extension as? LibraryExtension)
                ?: throw RuntimeException("calling aidlPackagedList on non Library variant")

            return libExt.aidlPackagedList
        }

    override val bundleOptions: Bundle
        get() = (extension as? ApplicationExtension)?.bundle
            ?: throw RuntimeException("calling BundleOptions on non Application variant")

    override val compileOptions: CompileOptions
        get() = extension.compileOptions

    override val compileOptionsIncremental: Boolean?
        get() = oldExtension.compileOptions.incremental

    override val composeOptions: ComposeOptions
        get() = extension.composeOptions

    override val dataBinding: DataBinding
        get() = extension.dataBinding

    override val deviceProviders: List<DeviceProvider>
        get() = oldExtension.deviceProviders

    override val externalNativeBuild: ExternalNativeBuild
        get() = extension.externalNativeBuild

    override val installationOptions: Installation
        get() = extension.installation

    override val libraryRequests: Collection<LibraryRequest>
        get() = extension.libraryRequests

    override val lintOptions: Lint
        get() = extension.lint

    override val resourcePrefix: String?
        get() = extension.resourcePrefix

    override val splits: Splits
        get() = extension.splits

    override val prefab: Set<PrefabPackagingOptions>
        get() = (extension as? LibraryExtension)?.prefab
            ?: throw RuntimeException("calling prefab on non Library variant")

    override val testCoverage: TestCoverage
        get() = extension.testCoverage

    override val testOptions: TestOptions
        get() = extension.testOptions

    override val testServers: List<TestServer>
        get() = oldExtension.testServers

    override val transforms: List<Transform>
        get() = oldExtension.transforms

    override val transformsDependencies: List<List<Any>>
        get() = oldExtension.transformsDependencies

    override val namespacedAndroidResources: Boolean
        get() = extension.androidResources.namespaced

    override val testOptionExecutionEnum: com.android.builder.model.TestOptions.Execution? by lazy {
        testOptions.execution.toExecutionEnum()
    }

    override val prefabOrEmpty: Set<PrefabPackagingOptions>
        get() = (extension as? LibraryExtension)?.prefab ?: setOf()

    override val hasNoBuildTypeMinified: Boolean
        get() = extension.buildTypes.none { it.isMinifyEnabled }

    override val legacyLanguageSplitOptions: LanguageSplitOptions
        get() = oldExtension.splits.language
    override val manifestArtifactType: InternalArtifactType<Directory>
        get() = if (services.projectOptions[BooleanOption.IDE_DEPLOY_AS_INSTANT_APP])
            InternalArtifactType.INSTANT_APP_MANIFEST
        else
            InternalArtifactType.PACKAGED_MANIFESTS

    // Internal Objects

    override val globalArtifacts: ArtifactsImpl = ArtifactsImpl(project, "global")

    override val createdBy: String = "Android Gradle ${Version.ANDROID_GRADLE_PLUGIN_VERSION}"

    override val asmApiVersion = ASM_API_VERSION_FOR_INSTRUMENTATION

    // Utility methods

    override val platformAttrs: FileCollection by lazy {
        val attributes =
            Action { container: AttributeContainer ->
                container.attribute(
                    AndroidArtifacts.ARTIFACT_TYPE,
                    AndroidArtifacts.TYPE_PLATFORM_ATTR
                )
            }
        androidJar
            .incoming
            .artifactView { config -> config.attributes(attributes) }
            .artifacts
            .artifactFiles
    }

    override val localCustomLintChecks: FileCollection by lazy {
        getLocalCustomLintChecks(lintChecks)
    }

    override val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>
        get() = versionedSdkLoaderService.versionedSdkLoader

    override val versionedNdkHandler: SdkComponentsBuildService.VersionedNdkHandler by lazy {
        getBuildService<SdkComponentsBuildService>(services.buildServiceRegistry)
            .get()
            .versionedNdkHandler(compileSdkHashString, ndkVersion, ndkPath)
    }

    override val jarCreatorType: JarCreatorType
        get() = if (services.projectOptions.get(BooleanOption.USE_NEW_JAR_CREATOR)) {
            JarCreatorType.JAR_FLINGER
        } else {
            JarCreatorType.JAR_MERGER
        }

    override val apkCreatorType: ApkCreatorType
        get() = if (services.projectOptions.get(BooleanOption.USE_NEW_APK_CREATOR)) {
            ApkCreatorType.APK_FLINGER
        } else {
            ApkCreatorType.APK_Z_FILE_CREATOR
        }
}
