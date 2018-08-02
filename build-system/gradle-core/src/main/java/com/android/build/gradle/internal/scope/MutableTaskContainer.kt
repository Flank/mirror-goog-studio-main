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

package com.android.build.gradle.internal.scope

import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.NdkCompile
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.RenderscriptCompile
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

/**
 * DO NOT ADD NEW TASKS TO THIS CLASS.
 *
 * Container for the tasks for a variant.
 *
 * This contains 2 different types of tasks.
 * - the tasks needed by the variant API. The goal here is to revamp the API to get rid of the need
 *   to expose the tasks.
 * - tasks for internal usage and wiring. This should not be needed, except in rare cases (anchors).
 *   The goal is to get rid of this as much as possible, progressively; and to use buildable
 *   artifact exclusively to wire tasks.
 *
 * DO NOT ADD NEW TASKS TO THIS CLASS.
 */
class MutableTaskContainer : TaskContainer {

    // implementation of the API setter/getters as required by our current APIs.
    override lateinit var assembleTask: Task
    override lateinit var javacTask: JavaCompile
    override lateinit var compileTask: Task
    override lateinit var preBuildTask: Task
    override var checkManifestTask: CheckManifest? = null
    override var aidlCompileTask: AidlCompile? = null
    override var renderscriptCompileTask: RenderscriptCompile? = null
    override lateinit var mergeResourcesTask: MergeResources
    override lateinit var mergeAssetsTask: MergeSourceSetFolders
    override lateinit var processJavaResourcesTask: Sync
    override var generateBuildConfigTask: GenerateBuildConfig? = null
    override var ndkCompileTask: NdkCompile? = null
    override var obfuscationTask: Task? = null
    override var processAndroidResTask: ProcessAndroidResources? = null
    override var processManifestTask: TaskProvider<out ManifestProcessorTask>? = null
    override var packageAndroidTask: PackageAndroidArtifact? = null
    override var bundleLibraryTask: Zip? = null

    override var installTask: DefaultTask? = null
    override var uninstallTask: DefaultTask? = null

    override var connectedTestTask: DeviceProviderInstrumentTestTask? = null
    override val providerTestTaskList: List<DeviceProviderInstrumentTestTask> = mutableListOf()

    override var generateAnnotationsTask: ExtractAnnotations? = null

    override val externalNativeBuildTasks: MutableCollection<ExternalNativeBuildTask> = mutableListOf()

    // anything below is scheduled for removal, using BuildableArtifact to link tasks.

    var bundleTask: Task? = null
    var sourceGenTask: Task? = null
    var resourceGenTask: Task? = null
    var assetGenTask: Task? = null
    var connectedTask: Task? = null
    var microApkTask: Task? = null
    var externalNativeBuildTask: ExternalNativeBuildTask? = null
    var externalNativeJsonGenerator: ExternalNativeJsonGenerator? = null
    var packageSplitResourcesTask: Task? = null
    var packageSplitAbiTask: Task? = null
    var generateResValuesTask: Task? = null
    var generateApkDataTask: Task? = null
    var coverageReportTask: Task? = null
    var dataBindingExportBuildInfoTask: Task? = null
}