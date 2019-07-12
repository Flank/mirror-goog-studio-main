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
import com.android.SdkConstants.FN_CLASSES_JAR
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.packaging.JarCreatorFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.zip.Deflater
import javax.inject.Inject

private val CLASS_PATTERN = Pattern.compile(".*\\.class$")
private val META_INF_PATTERN = Pattern.compile("^META-INF/.*$")

/**
 * Bundle all library classes in a jar. Additional filters can be specified, in addition to ones
 * defined in [LibraryAarJarsTask.getDefaultExcludes].
 */
abstract class BundleLibraryClasses : NonIncrementalTask() {

    private lateinit var toIgnoreRegExps: Supplier<List<String>>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Internal
    lateinit var packageName: Lazy<String>
        private set

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:Input
    var packageBuildConfig: Boolean = false
        private set

    @get:Input
    abstract val packageRClass: Property<Boolean>

    @Input
    fun getToIgnore() = toIgnoreRegExps.get()

    @get:Input
    lateinit var jarCreatorType: JarCreatorType
        private set

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleLibraryClassesRunnable::class.java,
                BundleLibraryClassesRunnable.Params(
                    packageName = packageName.value,
                    toIgnore = toIgnoreRegExps.get(),
                    output = output.get().asFile,
                    input = classes.files,
                    packageBuildConfig = packageBuildConfig,
                    packageRClass = packageRClass.get(),
                    jarCreatorType = jarCreatorType
                )
            )
        }
    }

    class CreationAction(
        scope: VariantScope,
        private val publishedType: PublishedConfigType,
        private val toIgnoreRegExps: Supplier<List<String>> = Supplier { emptyList<String>() }
    ) :
        VariantTaskCreationAction<BundleLibraryClasses>(scope) {

        private val inputs: FileCollection

        init {
            check(
                publishedType == PublishedConfigType.API_ELEMENTS
                        || publishedType == PublishedConfigType.RUNTIME_ELEMENTS
            ) { "Library classes bundling is supported only for api and runtime." }

            // Because ordering matters for TransformAPI, we need to fetch classes from the
            // transform pipeline as soon as this creation action is instantiated.
            inputs = if (publishedType == PublishedConfigType.RUNTIME_ELEMENTS) {
                scope.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    types.contains(QualifiedContent.DefaultContentType.CLASSES)
                            && scopes.size == 1 && scopes.contains(QualifiedContent.Scope.PROJECT)
                }
            } else {
                variantScope.artifacts.getAllClasses()
            }
        }

        override val name: String =
            scope.getTaskName(
                if (publishedType == PublishedConfigType.API_ELEMENTS)
                    "bundleLibCompile"
                else
                    "bundleLibRuntime"
            )

        override val type: Class<BundleLibraryClasses> = BundleLibraryClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleLibraryClasses>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                if (publishedType == PublishedConfigType.API_ELEMENTS)
                    InternalArtifactType.COMPILE_LIBRARY_CLASSES
                else InternalArtifactType.RUNTIME_LIBRARY_CLASSES,
                BuildArtifactsHolder.OperationType.APPEND,
                taskProvider,
                BundleLibraryClasses::output,
                fileName = FN_CLASSES_JAR
            )
        }

        override fun configure(task: BundleLibraryClasses) {
            super.configure(task)

            task.packageName = lazy { variantScope.variantConfiguration.packageFromManifest }
            task.classes.from(inputs)
            val packageRClass =
                variantScope.globalScope.projectOptions[BooleanOption.COMPILE_CLASSPATH_LIBRARY_R_CLASSES] &&
                        publishedType == PublishedConfigType.API_ELEMENTS &&
                        !variantScope.globalScope.extension.aaptOptions.namespaced
            task.packageRClass.set(packageRClass)
            if (packageRClass) {
                task.classes.from(variantScope.artifacts.getFinalProduct<RegularFile>(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR))
            }
            // FIXME pass this as List<TextResources>
            task.toIgnoreRegExps = TaskInputHelper.memoize(toIgnoreRegExps)
            task.packageBuildConfig = variantScope.globalScope.extension.packageBuildConfig
            task.jarCreatorType = variantScope.jarCreatorType
        }
    }
}

/** Packages files to jar using the provided filter. */
class BundleLibraryClassesRunnable @Inject constructor(private val params: Params) : Runnable {
    data class Params(
        val packageName: String,
        val toIgnore: List<String>,
        val output: File,
        val input: Set<File>,
        val packageBuildConfig: Boolean,
        val packageRClass: Boolean,
        val jarCreatorType: JarCreatorType
    ) :
        Serializable

    override fun run() {
        Files.deleteIfExists(params.output.toPath())
        params.output.parentFile.mkdirs()

        val ignorePatterns =
            (LibraryAarJarsTask.getDefaultExcludes(
                packagePath = params.packageName,
                packageBuildConfig = params.packageBuildConfig,
                packageR = params.packageRClass
            ) + params.toIgnore)
                .map { Pattern.compile(it) }

        val predicate = Predicate<String> { entry ->
            (CLASS_PATTERN.matcher(entry).matches() || META_INF_PATTERN.matcher(entry).matches())
                    && !ignorePatterns.any { it.matcher(entry).matches() }
        }

        JarCreatorFactory.make(
            params.output.toPath(),
            predicate,
            params.jarCreatorType
        ).use { out ->
            // Don't compress because compressing takes extra time, and this jar doesn't go into any
            // APKs or AARs
            out.setCompressionLevel(Deflater.NO_COMPRESSION)
            params.input.forEach { base ->
                if (base.isDirectory) {
                    out.addDirectory(base.toPath())
                } else if (base.toString().endsWith(SdkConstants.DOT_JAR)) {
                    out.addJar(base.toPath())
                }
            }
        }
    }
}