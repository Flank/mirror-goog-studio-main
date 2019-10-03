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
package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.build.VariantOutput
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.symbols.processLibraryMainSymbolTable
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.google.common.base.Strings
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.io.Serializable
import javax.inject.Inject

@CacheableTask
abstract class GenerateLibraryRFileTask @Inject constructor(objects: ObjectFactory) : ProcessAndroidResources() {

    @get:OutputDirectory @get:Optional var sourceOutputDirectory= objects.directoryProperty(); private set

    @get:OutputFile @get:Optional var rClassOutputJar = objects.fileProperty()
        private set

    @Internal // rClassOutputJar is already marked as @OutputFile
    override fun getSourceOutputDir(): File? = rClassOutputJar.get().asFile

    // used by Butterknife
    @Suppress("unused")
    @Internal
    fun getTextSymbolOutputFile(): File {
        return textSymbolOutputFileProperty.get().asFile
    }

    @get:OutputFile abstract val textSymbolOutputFileProperty: RegularFileProperty

    @get:OutputFile abstract val symbolsWithPackageNameOutputFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE) abstract val dependencies: ConfigurableFileCollection

    @get:Input lateinit var packageForR: Provider<String> private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY) lateinit var platformAttrRTxt: FileCollection
        private set

    @get:Input lateinit var applicationId: Provider<String> private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localResourcesFile: RegularFileProperty

    @get:Input
    var namespacedRClass: Boolean = false
        private set

    @get:Input
    abstract val compileClasspathLibraryRClasses: Property<Boolean>

    @get:Input
    abstract val useConstantIds: Property<Boolean>

    @get:Input
    lateinit var multiOutputPolicy: MultiOutputPolicy
        private set

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        val manifest = chooseOutput(
            ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifestFiles))
            .outputFile

        getWorkerFacadeWithWorkers().use {
            it.submit(
                GenerateLibRFileRunnable::class.java,
                GenerateLibRFileParams(
                    localResourcesFile.get().asFile,
                    manifest,
                    platformAttrRTxt.singleFile,
                    dependencies.files,
                    packageForR.get(),
                    null,
                    rClassOutputJar.get().asFile,
                    textSymbolOutputFileProperty.get().asFile,
                    namespacedRClass,
                    compileClasspathLibraryRClasses.get(),
                    symbolsWithPackageNameOutputFile.get().asFile,
                    useConstantIds.get()
                )
            )
        }
    }

    private fun chooseOutput(manifestBuildElements: BuildElements): BuildOutput {
        when (multiOutputPolicy) {
            MultiOutputPolicy.SPLITS -> {
                val main = manifestBuildElements
                    .stream()
                    .filter { output -> output.apkData.type == VariantOutput.OutputType.MAIN }
                    .findFirst()
                if (!main.isPresent) {
                    throw RuntimeException("No main apk found")
                }
                return main.get()
            }
            MultiOutputPolicy.MULTI_APK -> {
                val nonDensity = manifestBuildElements
                    .stream()
                    .filter { output ->
                        output.apkData
                            .getFilter(
                                VariantOutput.FilterType
                                    .DENSITY
                            ) == null
                    }
                    .findFirst()
                if (!nonDensity.isPresent) {
                    throw RuntimeException("No non-density apk found")
                }
                return nonDensity.get()
            }
            else -> throw RuntimeException(
                "Unexpected MultiOutputPolicy type: $multiOutputPolicy"
            )
        }
    }

    data class GenerateLibRFileParams(
        val localResourcesFile: File,
        val manifest: File,
        val androidJar: File,
        val dependencies: Set<File>,
        val packageForR: String,
        val sourceOutputDirectory: File?,
        val rClassOutputJar: File?,
        val textSymbolOutputFile: File,
        val namespacedRClass: Boolean,
        val compileClasspathLibraryRClasses: Boolean,
        val symbolsWithPackageNameOutputFile: File,
        val useConstantIds: Boolean
    ) : Serializable

    class GenerateLibRFileRunnable @Inject constructor(private val params: GenerateLibRFileParams) : Runnable {
        override fun run() {
            val androidAttrSymbol = getAndroidAttrSymbols()

            val symbolTable = SymbolIo.readRDef(params.localResourcesFile.toPath())

            val idProvider =
                if (params.useConstantIds) {
                    IdProvider.constant()
                } else {
                    IdProvider.sequential()
                }
            processLibraryMainSymbolTable(
                librarySymbols = symbolTable,
                libraries = params.dependencies,
                mainPackageName = params.packageForR,
                manifestFile = params.manifest,
                sourceOut = params.sourceOutputDirectory,
                rClassOutputJar = params.rClassOutputJar,
                symbolFileOut = params.textSymbolOutputFile,
                platformSymbols = androidAttrSymbol,
                namespacedRClass = params.namespacedRClass,
                generateDependencyRClasses = !params.compileClasspathLibraryRClasses,
                idProvider = idProvider
            )

            SymbolIo.writeSymbolListWithPackageName(
                params.textSymbolOutputFile.toPath(),
                params.manifest.toPath(),
                params.symbolsWithPackageNameOutputFile.toPath())
        }

        private fun getAndroidAttrSymbols() =
            if (params.androidJar.exists())
                SymbolIo.readFromAapt(params.androidJar, "android")
            else
                SymbolTable.builder().tablePackage("android").build()
    }


    class CreationAction(variantScope: VariantScope, val isLibrary: Boolean)
        : VariantTaskCreationAction<GenerateLibraryRFileTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("generate", "RFile")
        override val type: Class<GenerateLibraryRFileTask>
            get() = GenerateLibraryRFileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out GenerateLibraryRFileTask>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processAndroidResTask = taskProvider

            variantScope.artifacts.producesFile(
                InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR,
                taskProvider,
                GenerateLibraryRFileTask::rClassOutputJar,
                fileName = "R.jar"
            )

            variantScope.artifacts.producesFile(
                InternalArtifactType.COMPILE_SYMBOL_LIST,
                taskProvider,
                GenerateLibraryRFileTask::textSymbolOutputFileProperty,
                SdkConstants.FN_RESOURCE_TEXT
            )

            // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created in
            // process resources for local subprojects.
            variantScope.artifacts.producesFile(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                taskProvider,
                GenerateLibraryRFileTask::symbolsWithPackageNameOutputFile,
                "package-aware-r.txt"
            )
        }


        override fun configure(task: GenerateLibraryRFileTask) {
            super.configure(task)

            val projectOptions = variantScope.globalScope.projectOptions

            task.platformAttrRTxt = variantScope.globalScope.platformAttrs

            task.applicationId = TaskInputHelper.memoizeToProvider(task.project) {
                variantScope.variantData.variantConfiguration.applicationId
            }

            if (!projectOptions[BooleanOption.NAMESPACED_R_CLASS]) {
                // Only include the dependency symbol tables when not using namespaced R classes.
                val consumedConfigType =
                    if (projectOptions[BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES]) {
                        COMPILE_CLASSPATH
                    } else {
                        RUNTIME_CLASSPATH
                    }
                task.dependencies.from(variantScope.getArtifactFileCollection(
                    consumedConfigType,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                ))
            }

            task.packageForR = TaskInputHelper.memoizeToProvider(task.project) {
                Strings.nullToEmpty(variantScope.variantConfiguration.originalApplicationId)
            }

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS, task.manifestFiles)

            task.namespacedRClass = projectOptions[BooleanOption.NAMESPACED_R_CLASS]

            task.compileClasspathLibraryRClasses.set(projectOptions[BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES])

            task.outputScope = variantScope.outputScope

            // This task can produce R classes with either constant IDs ("0") or sequential IDs
            // mimicking the way AAPT2 numbers IDs. If we're generating a compile time only R class
            // (either for the small merge in app or when using compile classpath resources in libs)
            // we want to use the constant IDs; otherwise, we will use sequential IDs.
            // In either case, the IDs are fake, and therefore are non-final.
            task.useConstantIds.set(
                (projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS] && !isLibrary)
                        || projectOptions[BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES])

            task.multiOutputPolicy = variantScope.variantData.multiOutputPolicy

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                task.localResourcesFile)
        }
    }
}
