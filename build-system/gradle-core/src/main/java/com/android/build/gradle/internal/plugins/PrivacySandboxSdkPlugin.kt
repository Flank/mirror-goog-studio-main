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
import com.android.build.api.dsl.PrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.InternalPrivacySandboxSdkExtension
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.PerModuleBundleTask
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.PackagePrivacySandboxSdkBundle
import com.android.build.gradle.tasks.PrivacySandboxSdkDexTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestGeneratorTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestMergerTask
import com.android.build.gradle.tasks.PrivacySandboxSdkMergeDexTask
import com.android.build.gradle.tasks.PrivacySandboxSdkMergeResourcesTask
import com.android.builder.core.ComponentTypeImpl
import com.android.repository.Revision
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

class PrivacySandboxSdkPlugin @Inject constructor(
    softwareComponentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
): AbstractFusedLibraryPlugin<PrivacySandboxSdkVariantScope>(softwareComponentFactory, listenerRegistry) {

    private val versionedSdkLoaderService: VersionedSdkLoaderService by lazy {
        withProject("versionedSdkLoaderService") { project ->
            VersionedSdkLoaderService(
                dslServices,
                project,
                {
                    variantScope.compileSdkVersion
                },
                {
                    Revision.parseRevision(extension.buildToolsVersion, Revision.Precision.MICRO)
                },
            )
        }
    }

    // so far, there is only one variant.
    override val variantScope by lazy {
        withProject("variantScope") { project ->
            PrivacySandboxSdkVariantScope(
                project,
                TaskCreationServicesImpl(projectServices),
                { extension },
                {
                    BootClasspathConfigImpl(
                        project,
                        projectServices,
                        versionedSdkLoaderService,
                        null,
                        false
                    )
                }
            )
        }
    }

    private val extension: PrivacySandboxSdkExtension by lazy {
        withProject("extension") { project ->
            instantiateExtension(project)
        }
    }

    override fun configureProject(project: Project) {
    }

    override fun configureExtension(project: Project) {
        extension
    }

    override fun maybePublishToMaven(
        project: Project,
        includeApiElements: Configuration,
        includeRuntimeElements: Configuration,
        includeRuntimeUnmerged: Configuration
    ) {
        // ASB only get published to Play Store, not maven
    }

    override fun apply(project: Project) {
        super.apply(project)
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
    }

    private fun instantiateExtension(project: Project): PrivacySandboxSdkExtension {

        val sdkLibraryExtensionImpl= dslServices.newDecoratedInstance(
            PrivacySandboxSdkExtensionImpl::class.java,
            dslServices,
        )

        abstract class Extension(
            val publicExtensionImpl: PrivacySandboxSdkExtensionImpl,
        ): InternalPrivacySandboxSdkExtension by publicExtensionImpl

        return project.extensions.create(
            PrivacySandboxSdkExtension::class.java,
            "android",
            Extension::class.java,
            sdkLibraryExtensionImpl
        )
    }

    override fun createTasks(project: Project) {
        createTasks(
                project,
                variantScope,
                listOf(
                        AppMetadataTask.PrivacySandboxSdkCreationAction(variantScope),
                        FusedLibraryBundleClasses.CreationAction(variantScope),
                        FusedLibraryClassesRewriteTask.CreationAction(variantScope),
                        FusedLibraryMergeClasses.CreationAction(variantScope),
                        MergeJavaResourceTask.CreationActionFusedLibrary(variantScope),
                        PrivacySandboxSdkMergeResourcesTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestGeneratorTask.CreationAction(variantScope),
                        PrivacySandboxSdkManifestMergerTask.CreationAction(variantScope),
                        PrivacySandboxSdkLinkAndroidResourcesTask.CreationAction(variantScope),
                        PrivacySandboxSdkDexTask.CreationAction(variantScope),
                        PrivacySandboxSdkMergeDexTask.CreationAction(variantScope),
                        PerModuleBundleTask.PrivacySandboxSdkCreationAction(variantScope),
                        PackagePrivacySandboxSdkBundle.CreationAction(variantScope),
                ) + FusedLibraryMergeArtifactTask.getCreationActions(variantScope),
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType  =
        GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK

    /**
     * ASB only get published to Play Store, not maven
     */
    override val artifactForPublication: Artifact.Single<RegularFile>? = PrivacySandboxSdkInternalArtifactType.ASB

    override val artifactTypeForPublication: AndroidArtifacts.ArtifactType
        get() = AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE

    override val allowUnmergedArtifacts: Boolean
        get() = false
}
