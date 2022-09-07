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

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.builder.dexing.ClassFileInput.CLASS_MATCHER
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.FileOutputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Task generates empty jar containing all classes to be included in a privacy sandbox sdk.
 */
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
@DisableCachingByDefault
abstract class PrivacySandboxSdkGenerateJarStubsTask : DefaultTask() {

    @get:Classpath
    abstract val mergedClasses: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputStubJar: RegularFileProperty

    @TaskAction
    fun doTaskAction() {
        val dotClassFiles = mergedClasses.asFileTree
                .filterNotNull()
                .filter { CLASS_MATCHER.test(it.invariantSeparatorsPath) }
        JarOutputStream(FileOutputStream(outputStubJar.get().asFile)).use {
            outputStream ->
            for (clazz in dotClassFiles) {
                val zipEntry =
                        ZipEntry(clazz.relativeTo(mergedClasses.singleFile).invariantSeparatorsPath)
                outputStream.putNextEntry(zipEntry)
                clazz.inputStream().use { inputStream ->
                    outputStream.write(inputStream.readBytes())
                }
                outputStream.closeEntry()
            }
        }
    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope) :
            TaskCreationAction<PrivacySandboxSdkGenerateJarStubsTask>() {

        override val name: String
            get() = "privacySandboxClassesJarStubs"

        override val type: Class<PrivacySandboxSdkGenerateJarStubsTask>
            get() = PrivacySandboxSdkGenerateJarStubsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxSdkGenerateJarStubsTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxSdkGenerateJarStubsTask::outputStubJar
            ).withName(privacySandboxSdkStubJarFilename)
                    .on(PrivacySandboxSdkInternalArtifactType.STUB_JAR)
        }

        override fun configure(task: PrivacySandboxSdkGenerateJarStubsTask) {
            task.mergedClasses.fromDisallowChanges(
                    creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_CLASSES)
            )
        }
    }

    companion object {

        const val privacySandboxSdkStubJarFilename = "sdk-interface-descriptors.jar"
    }
}

