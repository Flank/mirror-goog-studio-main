/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.packaging.JarMerger
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task that zips a single module into a zip file.
 *
 * This is an input for [BundleTask]
 */
open class ZipModuleTask: DefaultTask() {

    @get:Internal
    private lateinit var outputFile: Provider<RegularFile>
        private set
    @get:Internal
    private lateinit var configuration: Configuration
        private set

    @get:Internal
    private val artifactMap: MutableMap<AndroidArtifacts.ArtifactType, FileCollection> = mutableMapOf()

    @get:Internal
    private val _combinedInputs: ConfigurableFileCollection = project.files()

    @Suppress("unused", "MemberVisibilityCanPrivate")
    @get:InputFiles
    val combinedInputs: FileCollection
        get() {
            computeInputs()
            return _combinedInputs
        }

    @Suppress("MemberVisibilityCanPrivate")
    @get:OutputFile
    val outputBundle: File
    get() = outputFile.get().asFile

    @TaskAction
    fun zip() {
        val jarMerger = JarMerger(outputBundle.toPath())

        jarMerger.use { it ->

            for ((artifactType, fileCollection) in artifactMap.entries) {
                val relocator = Relocator(artifactType)

                val artifactFile = fileCollection.singleFile
                println("=> $artifactFile")

                when (artifactType) {
                    AndroidArtifacts.ArtifactType.ASSETS -> {
                        it.addDirectory(
                                artifactFile.toPath(),
                                null,
                                null,
                                relocator)
                    }

                    AndroidArtifacts.ArtifactType.RES_BUNDLE -> {
                        // the file is a zip.
                        it.addJar(artifactFile.toPath(), null, ResRelocator())
                    }

                    else -> {
                        // in this case the artifact is a folder containing things to add
                        // to the zip. Thse can be file to put directly, jars to copy the content
                        // of, or folders
                        for (file in artifactFile.listFiles()) {
                            if (file.isFile) {
                                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                                    it.addJar(file.toPath(), null, relocator)
                                } else {
                                    it.addFile(relocator.relocate(file.name), file.toPath())
                                }
                            } else {
                                it.addDirectory(
                                        file.toPath(),
                                        null,
                                        null,
                                        relocator)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes all the inputs from the [Configuration] objects.
     *
     * This queries the configuration for all the artifacts that need to be zipped up together.
     */
    private fun computeInputs() {
        if (artifactMap.isEmpty()) {
            prepareCollection(AndroidArtifacts.ArtifactType.ASSETS)
            prepareCollection(AndroidArtifacts.ArtifactType.DEX)
            prepareCollection(AndroidArtifacts.ArtifactType.JAVA_RES)
            prepareCollection(AndroidArtifacts.ArtifactType.JNI)
            prepareCollection(AndroidArtifacts.ArtifactType.RES_BUNDLE)
        }
    }

    private fun prepareCollection(
            artifactType: AndroidArtifacts.ArtifactType) {

        val artifactView = configuration.incoming.artifactView {
            it.attributes {
                it.attribute(AndroidArtifacts.ARTIFACT_TYPE, artifactType.type)
            }
        }

        val collection = artifactView.artifacts.artifactFiles

        _combinedInputs.from(collection)
        artifactMap[artifactType] = collection
    }

    class ConfigAction(
            private val moduleOutput: Provider<RegularFile>,
            private val config: Configuration) : Action<ZipModuleTask> {
        override fun execute(task: ZipModuleTask) {
            task.outputFile = moduleOutput
            task.configuration = config
        }
    }
}

class Relocator(private val artifactType: AndroidArtifacts.ArtifactType): JarMerger.Relocator {
    override fun relocate(entryPath: String) = when(artifactType) {
        AndroidArtifacts.ArtifactType.JNI -> entryPath
        else -> "${artifactType.pathPrefix}/$entryPath"
    }
}

class ResRelocator : JarMerger.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

