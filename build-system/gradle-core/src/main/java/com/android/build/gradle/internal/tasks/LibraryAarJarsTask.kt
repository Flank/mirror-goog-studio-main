/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import zipflinger.JarCreator
import com.android.builder.packaging.JarMerger
import com.android.builder.packaging.TypedefRemover
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.io.Closer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkerExecutor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.InvalidPathException
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.jar.JarEntry
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * A Task that takes the project/project local jars for CLASSES and RESOURCES, and
 * processes and combines them, and put them in the bundle folder.
 *
 *
 * This creates a main jar with the classes from the main scope, and all the java resources, and
 * a set of local jars (which are mostly the same as before except without the resources). This is
 * used to package the AAR.
 */


// TODO(b/132975663): add workers
@CacheableTask
abstract class LibraryAarJarsTask
@Inject constructor(workerExecutor: WorkerExecutor) : NonIncrementalTask() {
    abstract var excludeListProvider: Supplier<List<String>>
        protected set

    abstract var packageNameSupplier: Supplier<String>
        protected set

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val typedefRecipe: RegularFileProperty

    @get:Input
    abstract var packageBuildConfig: Boolean
        protected set

    @Input
    fun getExcludeList() = excludeListProvider.get()

    @Input
    fun getPackageName() = packageNameSupplier.get()

    @get:Classpath
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract var mainScopeClassFiles: FileCollection
        protected set

    @get:Classpath
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract var mainScopeResourceFiles: FileCollection
        protected set

    @get:Classpath
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract var localScopeInputFiles: FileCollection
        protected set

    @get:OutputFile
    abstract val mainClassLocation: RegularFileProperty

    @get:OutputDirectory
    abstract val localJarsLocation: DirectoryProperty


    override fun doTaskAction() {
        // non incremental task, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        FileUtils.deleteDirectoryContents(localJarsLocation.asFile.get())

        if (typedefRecipe.isPresent && !typedefRecipe.get().asFile.exists()) {
            throw IllegalStateException("Type def recipe not found: $typedefRecipe")
        }

        val excludePatterns = computeExcludeList()

        if (mainScopeClassFiles.isEmpty && mainScopeResourceFiles.isEmpty) {
            throw RuntimeException("Empty Main scope for $name")
        }

        mergeInputs(
            localScopeInputFiles.files,
            localJarsLocation.asFile.get(),
            mainScopeClassFiles.files,
            mainScopeResourceFiles.files,
            mainClassLocation.asFile.get(),
            Predicate { archivePath: String -> excludePatterns.any {
                it.matcher(archivePath).matches() }.not() },
            if (typedefRecipe.isPresent) {
                TypedefRemover()
                    .setTypedefFile(typedefRecipe.get().asFile)
            } else {
                null
            }
        )
    }

    private fun computeExcludeList(): List<Pattern> {
        val excludes = getDefaultExcludes(
            getPackageName().replace(".", "/"), packageBuildConfig)

        excludes.addAll(getExcludeList())

        // create Pattern Objects.
        return excludes.map { Pattern.compile(it) }
    }

    companion object {

        fun mergeInputs(
            localJars: MutableSet<File>,
            localJarsLocation: File,
            classFiles: MutableSet<File>,
            resourceFiles: MutableSet<File>,
            toFile: File,
            filter: Predicate<String>,
            typedefRemover: JarCreator.Transformer?) {

            // process main scope.
            mergeInputsToLocation(
                classFiles,
                resourceFiles,
                toFile,
                filter,
                typedefRemover
            )

            // process local scope
            processLocalJars(localJars, localJarsLocation)
        }


        private fun processLocalJars(inputs: MutableSet<File>, localJarsLocation: File) {

            /*
             * Separate jar and dir inputs, then copy the jars (almost) as is
             * then we'll make a single jars that contains all the folders
             * (though it's unlikely to happen)
             * Note that we do need to remove the resources
             * from the jars since they have been merged somewhere else.
             */

            val jarInputs = inputs.filter { it.name.endsWith(SdkConstants.DOT_JAR) }
            val dirInputs = inputs.filter { !it.name.endsWith(SdkConstants.DOT_JAR) }

            for (jar in jarInputs) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                copyJarWithContentFilter(
                    jar,
                    File(localJarsLocation, jar.name),
                    JarMerger.CLASSES_ONLY
                )
            }

            // now handle the folders.
            if (!dirInputs.isEmpty()) {
                JarMerger(
                    File(localJarsLocation, "otherclasses.jar").toPath(),
                    JarMerger.CLASSES_ONLY
                ).use { jarMerger ->
                    for (dir in dirInputs) {
                        jarMerger.addDirectory(dir.toPath())
                    }
                }
            }
        }

        private fun mergeInputsToLocation(
            classFiles: MutableSet<File>,
            resourceFiles: MutableSet<File>,
            toFile: File,
            filter: Predicate<String>,
            typedefRemover: JarCreator.Transformer?
        ) {
            val filterAndOnlyClasses = JarMerger.CLASSES_ONLY.and(filter)

            JarMerger(toFile.toPath()).use { jarMerger ->
                // Merge only class files on CLASS type inputs
                for (input in classFiles) {
                    // Skip if file doesn't exist
                    if (!input.exists()) {
                        continue
                    }

                    if (input.name.endsWith(SdkConstants.DOT_JAR)) {
                        jarMerger.addJar(input.toPath(), filterAndOnlyClasses, null)
                    } else {
                        jarMerger.addDirectory(
                            input.toPath(), filterAndOnlyClasses, typedefRemover, null)
                    }
                }

                for (input in resourceFiles) {
                    // Skip if file doesn't exist
                    if (!input.exists()) {
                        continue
                    }

                    if (input.name.endsWith(SdkConstants.DOT_JAR)) {
                        jarMerger.addJar(input.toPath(), filter, null)
                    } else {
                        jarMerger.addDirectory(
                            input.toPath(), filter, typedefRemover, null)
                    }
                }
            }
        }


        fun getDefaultExcludes(
            packagePath: String, packageBuildConfig: Boolean
        ): MutableList<String> {
            val excludes = mutableListOf(
                // these must be regexp to match the zip entries
                ".*/R.class$",
                ".*/R\\$(.*).class$",
                "$packagePath/Manifest.class$",
                "$packagePath/Manifest\\$(.*).class$"
            )

            if (!packageBuildConfig) {
                excludes.add("$packagePath/BuildConfig.class$")
            }
            return excludes
        }

        // TODO(b/133510425): replace zip with zipflinger
        fun copyJarWithContentFilter(
            from: File, to: File, filter: Predicate<String>?
        ) {
            val buffer = ByteArray(4096)

            Closer.create().use { closer ->
                val fos = FileOutputStream(to)
                val bos = BufferedOutputStream(fos)
                val zos = closer.register(ZipOutputStream(bos))

                val fis = FileInputStream(from)
                val bis = BufferedInputStream(fis)
                val zis = closer.register(ZipInputStream(bis))

                // loop on the entries of the intermediary package
                // and put them in the final package.
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name

                    if (entry.isDirectory || filter != null && !filter.test(name)) {
                        entry = zis.nextEntry
                        continue
                    }

                    // Preserve the STORED method of the input entry.
                    val newEntry: JarEntry =
                        if (entry.method == JarEntry.STORED) {
                            JarEntry(entry)
                        } else {
                            // Create a new entry so that the compressed len is recomputed.
                            JarEntry(name)
                        }

                    if (!isValidZipEntryName(newEntry)) {
                        throw InvalidPathException(
                            newEntry.name, "Entry name contains invalid characters"
                        )
                    }

                    newEntry.lastModifiedTime = JarMerger.ZERO_TIME
                    newEntry.lastAccessTime = JarMerger.ZERO_TIME
                    newEntry.creationTime = JarMerger.ZERO_TIME

                    // add the entry to the jar archive
                    zos.putNextEntry(newEntry)

                    // read the content of the entry from the input stream, and write it into the
                    // archive.
                    var count: Int = zis.read(buffer)
                    while (count != -1) {
                        zos.write(buffer, 0, count)
                        count = zis.read(buffer)
                    }

                    zos.closeEntry()
                    zis.closeEntry()

                    entry = zis.nextEntry
                }
            }
        }
    }

    class CreationAction(
        variantScope: VariantScope,
        private val packageBuildConfig: Boolean,
        private val excludeListProvider: Supplier<List<String>> =  Supplier { listOf<String>() }
    ) : VariantTaskCreationAction<LibraryAarJarsTask>(variantScope) {
        override val type = LibraryAarJarsTask::class.java
        override val name =  variantScope.getTaskName("sync", "LibJars")

        override fun handleProvider(taskProvider: TaskProvider<out LibraryAarJarsTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesFile(
                artifactType = InternalArtifactType.AAR_MAIN_JAR,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                productProvider = LibraryAarJarsTask::mainClassLocation,
                fileName = SdkConstants.FN_CLASSES_JAR
            )

            variantScope.artifacts.producesDir(
                artifactType = InternalArtifactType.AAR_LIBS_DIRECTORY,
                operationType = BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider = taskProvider,
                productProvider = LibraryAarJarsTask::localJarsLocation,
                fileName = SdkConstants.LIBS_FOLDER
            )
        }

        override fun configure(task: LibraryAarJarsTask) {
            super.configure(task)

            task.excludeListProvider = excludeListProvider

            val artifacts = variantScope.artifacts

            if (artifacts.hasFinalProduct(InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE)) {
                artifacts.setTaskInputToFinalProduct<RegularFile>(
                    InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE,
                    task.typedefRecipe
                )
            }

            task.packageNameSupplier = Supplier {
                variantScope.variantConfiguration.packageFromManifest }

            task.packageBuildConfig = packageBuildConfig

            /*
             * Only get files that are CLASS, and exclude files that are both CLASS and RESOURCES
             * The first filter gets streams that CONTAIN classes, the second one gets
             * ONLY class content
             *
             * Files coming from the transform streams might not exist.
             * Need to check if they exist during the task action [mergeInputsToLocation],
             * which means gradle will have to deal with possibly non-existent files in the cache
             */
            task.mainScopeClassFiles = variantScope.transformManager
                .getPipelineOutputAsFileCollection (
                    {contentTypes, scopes ->
                        contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                                && scopes.contains(Scope.PROJECT)
                    },
                    {contentTypes, scopes ->
                        (contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                                && !contentTypes.contains(QualifiedContent.DefaultContentType.RESOURCES))
                                && scopes.contains(Scope.PROJECT)})

            task.mainScopeResourceFiles = variantScope.transformManager
                .getPipelineOutputAsFileCollection {contentTypes, scopes ->
                    contentTypes.contains(QualifiedContent.DefaultContentType.RESOURCES)
                            && scopes.contains(Scope.PROJECT)}

            task.localScopeInputFiles = variantScope.transformManager
                .getPipelineOutputAsFileCollection {contentTypes, scopes ->
                    (contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES)
                            || contentTypes.contains(QualifiedContent.DefaultContentType.RESOURCES))
                            && scopes.intersect(
                        TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS).isNotEmpty()
                            && !scopes.contains(Scope.PROJECT)}
        }
    }

}