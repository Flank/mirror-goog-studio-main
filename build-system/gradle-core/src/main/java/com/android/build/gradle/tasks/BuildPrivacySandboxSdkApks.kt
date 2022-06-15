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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.io.MoreFiles
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

/**
 * Task to extract the privacy sandbox SDK APKs for this app
 */
@DisableCachingByDefault(because="Task only extracts zips")
abstract class BuildPrivacySandboxSdkApks : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sdkApksArchives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val sdkApks: DirectoryProperty

    override fun doTaskAction() {
        FileUtils.cleanOutputDir(sdkApks.get().asFile)
        if (sdkApksArchives.isEmpty) {
            logger.lifecycle("There are no privacy sandbox SDK dependencies for ${projectPath.get()} $variantName ")
            return
        }
        val outputDirectory = sdkApks.get().asFile.toPath()
        for (archiveFile in sdkApksArchives) {
            val archive = archiveFile.toPath()
            ZipArchive(archive).use {
                it.getInputStream("standalones/standalone.apk").use { inputStream ->
                    // TODO(b/235469089) handle collisions
                    Files.copy(inputStream, outputDirectory.resolve(MoreFiles.getNameWithoutExtension(archive) + "-standalone.apk"))
                }
            }
        }
        // TODO(b/235469089) use bundle tool here
        val locations = sdkApksArchives.joinToString("\n - ", prefix = "\n - ") { it.absolutePath }
        logger.lifecycle("Privacy sandbox SDK APKs for ${projectPath.get()} $variantName have been built at:$locations")

    }

    class CreationAction(creationConfig: ApkCreationConfig) : VariantTaskCreationAction<BuildPrivacySandboxSdkApks, ApkCreationConfig>(
            creationConfig,
            dependsOnPreBuildTask = false
    ) {

        override val name: String
            get() = computeTaskName("buildPrivacySandboxSdkApksFor")
        override val type: Class<BuildPrivacySandboxSdkApks>
            get() = BuildPrivacySandboxSdkApks::class.java

        override fun handleProvider(taskProvider: TaskProvider<BuildPrivacySandboxSdkApks>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    BuildPrivacySandboxSdkApks::sdkApks
            ).on(InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs)
        }

        override fun configure(task: BuildPrivacySandboxSdkApks) {
            super.configure(task)
            task.sdkApksArchives.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_APKS
                    )
            )
        }
    }
}
