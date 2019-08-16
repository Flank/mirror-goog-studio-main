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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DEX
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MODULE_PATH
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.getDesugarLibDexFromTransform
import com.android.builder.files.NativeLibraryAbiPredicate
import com.android.builder.packaging.JarMerger
import com.android.builder.packaging.JarCreator
import com.android.utils.FileUtils
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.zip.Deflater

/**
 * Task that zips a module's bundle elements into a zip file. This gets published
 * so that the base app can package into the bundle.
 *
 */
abstract class PerModuleBundleTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var dexFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var featureDexFiles: FileCollection
        private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resFiles: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var javaResFiles: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsFiles: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var nativeLibsFiles: FileCollection
        private set

    @get:Input
    @get:Optional
    var abiFilters: Set<String>? = null
        private set

    private lateinit var fileNameSupplier: Supplier<String>

    @get:Input
    val fileName: String
        get() = fileNameSupplier.get()

    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        private set

    public override fun doTaskAction() {
        FileUtils.cleanOutputDir(outputDir.get().asFile)
        val jarCreator =
            JarCreatorFactory.make(File(outputDir.get().asFile, fileName).toPath(), jarCreatorType)

        // Disable compression for module zips, since this will only be used in bundletool and it
        // will need to uncompress them anyway.
        jarCreator.setCompressionLevel(Deflater.NO_COMPRESSION)

        val filters = abiFilters
        val abiFilter: Predicate<String>? = if (filters != null) NativeLibraryAbiPredicate(filters, false) else null

        jarCreator.use {
            it.addDirectory(
                assetsFiles.get().asFile.toPath(),
                null,
                null,
                Relocator(FD_ASSETS)
            )

            it.addJar(resFiles.get().asFile.toPath(), null, ResRelocator())

            // dex files
            val dexFilesSet = if (hasFeatureDexFiles()) featureDexFiles.files else dexFiles.files
            if (dexFilesSet.size == 1) {
                // Don't rename if there is only one input folder
                // as this might be the legacy multidex case.
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, Relocator(FD_DEX), null)
            } else {
                addHybridFolder(it, dexFilesSet.sortedBy { it.name }, DexRelocator(FD_DEX), null)
            }

            val javaResFilesSet = if (hasFeatureDexFiles()) setOf<File>() else javaResFiles.files
            addHybridFolder(it, javaResFilesSet,
                Relocator("root"),
                JarMerger.EXCLUDE_CLASSES)

            addHybridFolder(it, nativeLibsFiles.files, fileFilter = abiFilter)
        }
    }

    private fun addHybridFolder(
        jarCreator: JarCreator,
        files: Iterable<File>,
        relocator: JarCreator.Relocator? = null,
        fileFilter: Predicate<String>? = null ) {
        // in this case the artifact is a folder containing things to add
        // to the zip. These can be file to put directly, jars to copy the content
        // of, or folders
        for (file in files) {
            if (file.isFile) {
                if (file.name.endsWith(SdkConstants.DOT_JAR)) {
                    jarCreator.addJar(file.toPath(), fileFilter, relocator)
                } else if (fileFilter == null || fileFilter.test(file.name)) {
                    if (relocator != null) {
                        jarCreator.addFile(relocator.relocate(file.name), file.toPath())
                    } else {
                        jarCreator.addFile(file.name, file.toPath())
                    }
                }
            } else {
                jarCreator.addDirectory(
                    file.toPath(),
                    fileFilter,
                    null,
                    relocator)
            }
        }
    }

    private fun hasFeatureDexFiles() = featureDexFiles.files.isNotEmpty()

    class CreationAction(
        variantScope: VariantScope,
        private val packageCustomClassDependencies: Boolean
    ) : VariantTaskCreationAction<PerModuleBundleTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("build", "PreBundle")
        override val type: Class<PerModuleBundleTask>
            get() = PerModuleBundleTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out PerModuleBundleTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(InternalArtifactType.MODULE_BUNDLE,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                PerModuleBundleTask::outputDir)
        }

        override fun configure(task: PerModuleBundleTask) {
            super.configure(task)

            val artifacts = variantScope.artifacts
            task.fileNameSupplier = if (variantScope.type.isBaseModule)
                Supplier { "base.zip"}
            else {
                val featureName: Supplier<String> = FeatureSetMetadata.getInstance()
                    .getFeatureNameSupplierForTask(variantScope, task)
                Supplier { "${featureName.get()}.zip"}
            }

            artifacts.setTaskInputToFinalProduct(
                 InternalArtifactType.MERGED_ASSETS, task.assetsFiles)
            artifacts.setTaskInputToFinalProduct(
                    if (variantScope.useResourceShrinker()) {
                        InternalArtifactType.SHRUNK_LINKED_RES_FOR_BUNDLE
                    } else {
                        InternalArtifactType.LINKED_RES_FOR_BUNDLE
                    }, task.resFiles)

            val programDexFiles =
                if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.BASE_DEX)) {
                    variantScope
                        .artifacts
                        .getFinalProductAsFileCollection(InternalArtifactType.BASE_DEX).get()
                } else if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.DEX)) {
                    variantScope.globalScope.project.files(variantScope
                        .artifacts
                        .getFinalProducts<Directory>(InternalArtifactType.DEX))
                } else {
                    variantScope.transformManager.getPipelineOutputAsFileCollection(StreamFilter.DEX)
                }
            val desugarLibDexFile =
                if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.DESUGAR_LIB_DEX)) {
                    variantScope
                        .artifacts
                        .getFinalProductAsFileCollection(InternalArtifactType.DESUGAR_LIB_DEX)
                        .get()
                } else {
                    getDesugarLibDexFromTransform(variantScope)
                }

            task.dexFiles = programDexFiles.plus(desugarLibDexFile)

            task.featureDexFiles =
                variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.FEATURE_DEX,
                    mapOf(MODULE_PATH to variantScope.globalScope.project.path)
                )
            task.javaResFiles = if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.SHRUNK_JAVA_RES)) {
                variantScope.globalScope.project.layout.files(
                    artifacts.getFinalProduct<RegularFile>(InternalArtifactType.SHRUNK_JAVA_RES)
                )
            } else if (variantScope.needsMergedJavaResStream) {
                variantScope.transformManager
                    .getPipelineOutputAsFileCollection(StreamFilter.RESOURCES)
            } else {
                variantScope.globalScope.project.layout.files(
                    artifacts.getFinalProduct<RegularFile>(InternalArtifactType.MERGED_JAVA_RES)
                )
            }
            task.nativeLibsFiles = getNativeLibsFiles(variantScope, packageCustomClassDependencies)

            task.abiFilters = variantScope.variantConfiguration.supportedAbis

            task.jarCreatorType = variantScope.jarCreatorType
        }
    }
}

private class Relocator(private val prefix: String): JarCreator.Relocator {
    override fun relocate(entryPath: String) = "$prefix/$entryPath"
}

/**
 * Relocate the dex files into a single folder which might force renaming
 * the dex files into a consistent scheme : classes.dex, classes2.dex, classes4.dex, ...
 *
 * <p>Note that classes1.dex is not a valid dex file name</p>
 *
 * When dealing with native multidex, we can have several folders each containing 1 to many
 * dex files (with the same naming scheme). In that case, merge and rename accordingly, note
 * that only one classes.dex can exist in the merged location so the others will be renamed.
 *
 * When dealing with a single feature (base) in legacy multidex, we must not rename the
 * main dex file (classes.dex) as it contains the bootstrap code for the application.
 */
private class DexRelocator(private val prefix: String): JarCreator.Relocator {
    // first valid classes.dex with an index starts at 2.
    val index = AtomicInteger(2)
    val classesDexNameUsed = AtomicBoolean(false)
    override fun relocate(entryPath: String): String {
        if (entryPath.startsWith("classes")) {
            return if (entryPath == "classes.dex" && !classesDexNameUsed.get()) {
                classesDexNameUsed.set(true)
                "$prefix/classes.dex"
            } else {
                val entryIndex = index.getAndIncrement()
                "$prefix/classes$entryIndex.dex"
            }
        }
        return "$prefix/$entryPath"
    }
}


private class ResRelocator : JarCreator.Relocator {
    override fun relocate(entryPath: String) = when(entryPath) {
        SdkConstants.FN_ANDROID_MANIFEST_XML -> "manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML
        else -> entryPath
    }
}

/**
 * Returns a file collection containing all of the native libraries to be packaged.
 */
fun getNativeLibsFiles(
    scope: VariantScope,
    packageCustomClassDependencies: Boolean
): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    if (scope.type.isForTesting) {
        return nativeLibs.from(scope.artifacts.getFinalProduct<Directory>(MERGED_NATIVE_LIBS))
    }
    nativeLibs.from(scope.artifacts.getFinalProduct<Directory>(STRIPPED_NATIVE_LIBS))
    if (packageCustomClassDependencies) {
        nativeLibs.from(
            scope.transformManager.getPipelineOutputAsFileCollection(StreamFilter.NATIVE_LIBS)
        )
    }
    return nativeLibs
}