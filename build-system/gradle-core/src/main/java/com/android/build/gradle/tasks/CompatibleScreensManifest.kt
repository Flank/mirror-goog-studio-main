/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.resources.Density
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import java.io.File
import java.io.IOException

/**
 * Task to generate a manifest snippet that just contains a compatible-screens node with the given
 * density and the given list of screen sizes.
 */
@CacheableTask
abstract class CompatibleScreensManifest : NonIncrementalTask() {

    @get:Input
    lateinit var screenSizes: Set<String>
        internal set

    @get:OutputDirectory
    abstract val outputFolder: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkList: RegularFileProperty

    @get:Input
    @get:Optional
    lateinit var minSdkVersion: Provider<String?> internal set

    override fun doTaskAction() {

        BuildElements(
            ExistingBuildElements.loadApkList(apkList.get().asFile).mapNotNull {
            val generatedManifest = generate(it)
            if (generatedManifest != null)
                BuildOutput(COMPATIBLE_SCREEN_MANIFEST, it, generatedManifest)
            else
                null
        }.toList()).save(outputFolder.get().asFile)
    }

    private fun generate(apkData: ApkData): File? {
        val densityFilter = apkData.getFilter(VariantOutput.FilterType.DENSITY)
                ?: return null

        val content = StringBuilder()
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            .append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
            .append("    package=\"\${packageName}\">\n")
            .append("\n")
        if (minSdkVersion.isPresent) {
            content.append("    <uses-sdk android:minSdkVersion=\"")
                .append(minSdkVersion.get())
                .append("\"/>\n")
        }
        content.append("    <compatible-screens>\n")

        // convert unsupported values to numbers.
        val density = convert(densityFilter.identifier, Density.XXHIGH, Density.XXXHIGH)

        for (size in screenSizes) {
            content.append("        <screen android:screenSize=\"")
                .append(size)
                .append("\" " + "android:screenDensity=\"")
                .append(density).append("\" />\n")
        }

        content.append(
                "    </compatible-screens>\n" + "</manifest>"
        )

        val splitFolder = File(outputFolder.get().asFile, apkData.dirName)
        FileUtils.mkdirs(splitFolder)
        val manifestFile = File(splitFolder, SdkConstants.ANDROID_MANIFEST_XML)

        try {
            Files.asCharSink(manifestFile, Charsets.UTF_8).write(content.toString())
        } catch (e: IOException) {
            throw BuildException(e.message, e)
        }

        return manifestFile
    }

    private fun convert(density: String, vararg densitiesToConvert: Density): String {
        for (densityToConvert in densitiesToConvert) {
            if (densityToConvert.resourceValue == density) {
                return Integer.toString(densityToConvert.dpiValue)
            }
        }
        return density
    }

    class CreationAction(variantScope: VariantScope, private val screenSizes: Set<String>) :
        VariantTaskCreationAction<CompatibleScreensManifest>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("create", "CompatibleScreenManifests")
        override val type: Class<CompatibleScreensManifest>
            get() = CompatibleScreensManifest::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CompatibleScreensManifest>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                COMPATIBLE_SCREEN_MANIFEST,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                CompatibleScreensManifest::outputFolder
            )
        }

        override fun configure(task: CompatibleScreensManifest) {
            super.configure(task)

            task.screenSizes = screenSizes

            variantScope.artifacts.setTaskInputToFinalProduct(InternalArtifactType.APK_LIST,
                task.apkList)

            val config = variantScope.variantConfiguration
            task.minSdkVersion = TaskInputHelper.memoizeToProvider(task.project) {
                val minSdk = config.mergedFlavor.minSdkVersion
                minSdk?.apiString
            }
        }
    }
}
