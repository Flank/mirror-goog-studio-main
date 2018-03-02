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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ASSETS
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.DEX
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAVA_RES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JNI
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RES_BUNDLE
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_DEX
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PUBLISHED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RES_FOR_BUNDLE
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.builder.packaging.JarMerger
import com.android.tools.build.bundletool.BuildBundleCommand
import com.android.utils.FileUtils
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class BundleDynamicModules : AndroidVariantTask() {
    private lateinit var baseModuleArtifacts: Map<InternalArtifactType, FileCollection>
    private lateinit var dynamicModuleArtifacts: Map<AndroidArtifacts.ArtifactType, ArtifactCollection>

    private lateinit var zipsDirectory: File

    @get:OutputDirectory
    private lateinit var bundleDir: File

    @get:InputFiles
    @Suppress("unused")
    val baseModuleInputFiles: Iterable<FileCollection>
        get() = baseModuleArtifacts.values

    @get:InputFiles
    @Suppress("unused")
    lateinit var dynamicModuleInputFiles: Iterable<FileCollection>

    private lateinit var _configFile: File

    @get:Optional
    @get:InputFile
    @Suppress("MemberVisibilityCanPrivate")
    val configFile: File?
        get() = if (_configFile.exists()) _configFile else null

    @TaskAction
    fun bundleModules() {
        val moduleZipInputs = processExternalArtifacts()
        val localModuleZipInput = processInternalArtifacts()

        moduleZipInputs.add(localModuleZipInput)

        val moduleZipper = ModuleZipper(zipsDirectory)
        val zippedModules =
            moduleZipInputs.asSequence().map { moduleZipper.zipModule(it).toPath() }
                .toImmutableList()

        // BundleTool requires that the destination directory for the bundle file exists,
        // and that the bundle file itself does not
        FileUtils.mkdirs(bundleDir)
        val bundleFile = File(bundleDir, "bundle.aab")

        if (bundleFile.isFile) {
            FileUtils.delete(bundleFile)
        }
        val command = BuildBundleCommand.builder().setOutputPath(bundleFile.toPath())
            .setModulesPaths(zippedModules)
        configFile?.let {
            command.setBundleConfigPath(it.toPath())
        }

        command.build().execute()
    }

    private fun processInternalArtifacts(): ModuleZipInput {
        return ModuleZipInput(
            "base",
            baseModuleArtifacts[MERGED_ASSETS]!!.singleFile,
            baseModuleArtifacts[PUBLISHED_DEX]!!.singleFile,
            baseModuleArtifacts[PUBLISHED_JAVA_RES]!!.singleFile,
            baseModuleArtifacts[PUBLISHED_NATIVE_LIBS]!!.singleFile,
            baseModuleArtifacts[LINKED_RES_FOR_BUNDLE]!!.singleFile
        )
    }

    private fun processExternalArtifacts(): MutableList<ModuleZipInput> {
        return dynamicModuleArtifacts.asSequence().flatMap { (type, artifacts) ->
            artifacts.artifacts.asSequence().map { type to it }
        }.groupBy(
                { (_, artifact) -> artifact.id.componentIdentifier },
                { (type, artifact) -> (type to artifact.file) })
            .asSequence()
            .map { (componentId, pairList) ->
                val artifactMap = pairList.toMap()
                ModuleZipInput(
                    computeModuleName(componentId),
                    artifactMap[ASSETS] ?: throw IllegalStateException("ASSETS not found"),
                    artifactMap[DEX] ?: throw IllegalStateException("DEX not found"),
                    artifactMap[JAVA_RES]
                            ?: throw IllegalStateException("JAVA_RES not found"),
                    artifactMap[JNI] ?: throw IllegalStateException("JNI not found"),
                    artifactMap[RES_BUNDLE]
                            ?: throw IllegalStateException("RES_BUNDLE not found")
                )
            }
            .toMutableList()
    }

    // Compute an appropriate name for this module based on the ComponentIdentifier of the Artifacts
    private fun computeModuleName(componentId: ComponentIdentifier): String {
        val projectPath: String? = (componentId as? ProjectComponentIdentifier)?.projectPath
        return projectPath?.substring(1)
                ?: componentId.displayName
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<BundleDynamicModules> {
        companion object {
            private val TASK_OUTPUT_TYPES = listOf(
                MERGED_ASSETS,
                PUBLISHED_DEX,
                PUBLISHED_JAVA_RES,
                PUBLISHED_NATIVE_LIBS,
                LINKED_RES_FOR_BUNDLE
            )
            private val PUBLISHED_TYPES = listOf(
                ASSETS,
                DEX,
                JAVA_RES,
                JNI,
                RES_BUNDLE
            )
        }

        override fun getName() = scope.getTaskName("bundle")
        override fun getType() = BundleDynamicModules::class.java

        override fun execute(task: BundleDynamicModules) {
            task.variantName = scope.fullVariantName

            // Map ArtifactType enums to collections of build outputs
            task.baseModuleArtifacts = TASK_OUTPUT_TYPES.associate { it to scope.getOutput(it) }
            task.dynamicModuleArtifacts =
                    PUBLISHED_TYPES.associate { it to getArtifactCollection(it) }
            task.dynamicModuleInputFiles =
                    task.dynamicModuleArtifacts.values.map { it.artifactFiles }

            task.zipsDirectory = FileUtils.join(
                scope.globalScope.intermediatesDir,
                "zipped-modules",
                scope.variantConfiguration.dirName
            )

            task.bundleDir = FileUtils.join(
                scope.globalScope.outputsDir,
                "bundle",
                scope.variantConfiguration.dirName
            )

            task._configFile = scope.globalScope.project.file("BundleConfig.xml")
        }

        private fun getArtifactCollection(artifactType: AndroidArtifacts.ArtifactType): ArtifactCollection {
            return scope.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.MODULE,
                artifactType
            )
        }
    }
}

data class ModuleZipInput(
    val moduleName: String,
    val assets: File,
    val dex: File,
    val javaRes: File,
    val jni: File,
    val resBundle: File
)

class ModuleZipper(private val zipsDir: File) {
    fun zipModule(input: ModuleZipInput): File {
        val zipFile = File(zipsDir, "${input.moduleName}${SdkConstants.DOT_ZIP}")
        val jarMerger = JarMerger(zipFile.toPath())

        jarMerger.use { it ->
            it.addDirectory(
                input.assets.toPath(),
                null,
                null,
                Relocator(ASSETS)
            )

            it.addFolderArtifact(input.dex, DEX)
            it.addFolderArtifact(input.javaRes, JAVA_RES)
            it.addFolderArtifact(input.jni, JNI)

            // This file is a zip
            it.addJar(input.resBundle.toPath(), null, ResRelocator())
        }

        return zipFile
    }

    /**
     * When the artifact is a folder containing things to add to the zip, we add files/folders directly and copy the content of jars
     */
    private fun JarMerger.addFolderArtifact(
        folder: File,
        artifactType: AndroidArtifacts.ArtifactType
    ) {
        val relocator = Relocator(artifactType)

        for (file in folder.listFiles()) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    this.addJar(file.toPath(), null, relocator)
                } else {
                    this.addFile(relocator.relocate(file.name), file.toPath())
                }
            } else {
                this.addDirectory(
                    file.toPath(),
                    null,
                    null,
                    relocator
                )
            }
        }
    }
}

class Relocator(private val artifactType: AndroidArtifacts.ArtifactType) : JarMerger.Relocator {
    override fun relocate(entryPath: String) = when (artifactType) {
        AndroidArtifacts.ArtifactType.JNI -> entryPath
        else -> "${artifactType.pathPrefix}/$entryPath"
    }
}

class ResRelocator : JarMerger.Relocator {
    override fun relocate(entryPath: String) = when (entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}