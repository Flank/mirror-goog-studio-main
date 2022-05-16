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
import com.android.build.gradle.internal.dsl.PrivacySandboxSdkExtensionImpl
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.tasks.FusedLibraryBundleClasses
import com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask
import com.android.build.gradle.tasks.FusedLibraryMergeClasses
import com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestGeneratorTask
import com.android.build.gradle.tasks.PrivacySandboxSdkManifestMergerTask
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

class PrivacySandboxSdkPlugin @Inject constructor(
    softwareComponentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
): AbstractFusedLibraryPlugin<PrivacySandboxSdkVariantScope>(softwareComponentFactory, listenerRegistry) {

    // so far, there is only one variant.
    override val variantScope by lazy {
        withProject("variantScope") { project ->
            PrivacySandboxSdkVariantScope(
                project
            ) { extension }
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

    private fun instantiateExtension(project: Project): PrivacySandboxSdkExtension {

        val sdkLibraryExtensionImpl= dslServices.newDecoratedInstance(
            PrivacySandboxSdkExtensionImpl::class.java,
            dslServices,
        )

        abstract class Extension(
            val publicExtensionImpl: PrivacySandboxSdkExtensionImpl,
        ): PrivacySandboxSdkExtension by publicExtensionImpl

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
                FusedLibraryClassesRewriteTask.CreateAction::class.java,
                PrivacySandboxSdkManifestGeneratorTask.CreationAction::class.java,
                PrivacySandboxSdkManifestMergerTask.CreationAction::class.java,
                FusedLibraryMergeResourcesTask.CreationAction::class.java,
                FusedLibraryMergeClasses.CreationAction::class.java,
                FusedLibraryBundleClasses.CreationAction::class.java,
            ),
        )
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType  =
        GradleBuildProject.PluginType.PRIVACY_SANDBOX_SDK

    /**
     * ASB only get published to Play Store, not maven
     */
    override val artifactTypeForPublication: Artifact.Single<RegularFile>? = null
}
