/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.builder.packaging.JarCreator
import com.android.builder.packaging.JarMerger
import com.android.builder.packaging.TypedefRemover
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.collect.Iterables
import com.google.common.io.Closer
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

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES, and
 * processes and combines them, and put them in the bundle folder.
 *
 *
 * This creates a main jar with the classes from the main scope, and all the java resources, and
 * a set of local jars (which are mostly the same as before except without the resources). This is
 * used to package the AAR.
 *
 *
 * Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the bundle folder.
 */
class LibraryAarJarsTransform(
    private val mainClassLocation: File,
    private val localJarsLocation: File,
    private val typedefRecipe: BuildableArtifact?,
    private val packageNameSupplier: Supplier<String>,
    private val packageBuildConfig: Boolean
) : Transform() {
    var excludeListProvider: Supplier<List<String>>? = null


    override fun getName(): String {
        return "syncLibJars"
    }

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_JARS
    }

    override fun getScopes(): MutableSet<in Scope> {
        return TransformManager.EMPTY_SCOPES
    }

    override fun getReferencedScopes(): MutableSet<in Scope> {
        return TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS
    }

    override fun isIncremental(): Boolean {
        // TODO make incremental
        return false
    }

    override fun getSecondaryFileOutputs(): Collection<File> {
        return listOf(mainClassLocation)
    }

    override fun getSecondaryFiles(): Collection<SecondaryFile> {
        return if (typedefRecipe != null) {
            listOf(SecondaryFile.nonIncremental(typedefRecipe))
        } else {
            listOf()
        }
    }

    override fun getSecondaryDirectoryOutputs(): Collection<File> {
        return if (localJarsLocation == null) {
            listOf()
        } else {
            listOf(localJarsLocation)
        }
    }

    override fun transform(invocation: TransformInvocation) {
        // non incremental transform, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        if (localJarsLocation != null) {
            FileUtils.deleteDirectoryContents(localJarsLocation)
        }
        if (typedefRecipe != null && !Iterables.getOnlyElement(typedefRecipe).exists()) {
            throw IllegalStateException("Type def recipe not found: $typedefRecipe")
        }

        val excludePatterns = computeExcludeList()

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        val mainScope = mutableListOf<QualifiedContent>()
        val localJarScope = mutableListOf<QualifiedContent>()

        for (input in invocation.referencedInputs) {
            for (qualifiedContent in Iterables.concat(
                input.jarInputs, input.directoryInputs
            )) {
                if (qualifiedContent.scopes.contains(Scope.PROJECT)) {
                    // even if the scope contains both project + local jar, we treat this as main
                    // scope.
                    mainScope.add(qualifiedContent)
                } else {
                    localJarScope.add(qualifiedContent)
                }
            }
        }

        // process main scope.
        if (mainScope.isEmpty()) {
            throw RuntimeException("Empty Main scope for $name")
        }

        mergeInputsToLocation(
            mainScope,
            mainClassLocation,
            Predicate { archivePath: String -> excludePatterns.any { it.matcher(archivePath).matches() }.not()},
            if (typedefRecipe != null) {
                TypedefRemover()
                    .setTypedefFile(Iterables.getOnlyElement(typedefRecipe))
            } else {
                null
            }
        )

        // process local scope
        processLocalJars(localJarScope)
    }

    private fun computeExcludeList(): List<Pattern> {
        val excludes = getDefaultExcludes(
            packageNameSupplier.get().replace(".", "/"), packageBuildConfig)
        if (excludeListProvider != null) {
            excludes.addAll(excludeListProvider!!.get())
        }

        // create Pattern Objects.
        return excludes.map { Pattern.compile(it) }
    }

    private fun processLocalJars(qualifiedContentList: MutableList<QualifiedContent>) {

        // Separate jar and dir inputs, then copy the jars (almost) as is
        // then we'll make a single jars that contains all the folders (though it's unlikely to
        // happen)
        // Note that we do need to remove the resources from the jars since they have been merged
        // somewhere else.

        val jarInputs = qualifiedContentList.filter { it is JarInput }
        val dirInputs = qualifiedContentList.filter { it !is JarInput }

        for (jar in jarInputs) {
            // we need to copy the jars but only take the class files as the resources have
            // been merged into the main jar.
            copyJarWithContentFilter(
                jar.file,
                File(localJarsLocation, jar.file.name),
                JarMerger.CLASSES_ONLY
            )
        }

        // now handle the folders.
        if (dirInputs.isNotEmpty()) {
            JarMerger(
                File(localJarsLocation, "otherclasses.jar").toPath(),
                JarMerger.CLASSES_ONLY
            ).use { jarMerger ->
                for (dir in dirInputs) {
                    jarMerger.addDirectory(dir.file.toPath())
                }
            }
        }
    }

    companion object {

        fun getDefaultExcludes(
            packagePath: String, packageBuildConfig: Boolean
        ): MutableList<String> {
            val excludes = mutableListOf(
                // these must be regexp to match the zip entries
                ".*/R.class$",
                ".*/R\\$(.*).class$",
                "$packagePath/Manifest.class$",
                "$packagePath/Manifest\\$(.*).class$")

            if (!packageBuildConfig) {
                excludes.add("$packagePath/BuildConfig.class$")
            }
            return excludes
        }

        fun copyJarWithContentFilter(
            from: File, to: File, filter: Predicate<String>?
        ) {
            val buffer = ByteArray(4096)

            Closer.create().use { closer ->
                val fos = closer.register(FileOutputStream(to))
                val bos = closer.register(BufferedOutputStream(fos))
                val zos = closer.register(ZipOutputStream(bos))

                val fis = closer.register(FileInputStream(from))
                val bis = closer.register(BufferedInputStream(fis))
                val zis = closer.register(ZipInputStream(bis))

                // loop on the entries of the intermediary package and put them in the final package.
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

        private fun mergeInputsToLocation(
            qualifiedContentList: List<QualifiedContent>,
            toFile: File,
            filter: Predicate<String>,
            typedefRemover: JarCreator.Transformer?
        ) {
            val filterAndOnlyClasses = JarMerger.CLASSES_ONLY.and(filter)

            JarMerger(toFile.toPath()).use { jarMerger ->
                for (content in qualifiedContentList) {
                    // merge only class files if RESOURCES are not in the scope
                    val hasResources = content.contentTypes
                        .contains(QualifiedContent.DefaultContentType.RESOURCES)
                    val thisFilter = if (hasResources) filter else filterAndOnlyClasses
                    if (content is JarInput) {
                        jarMerger.addJar(content.getFile().toPath(), thisFilter, null)
                    } else {
                        jarMerger.addDirectory(
                            content.file.toPath(), thisFilter, typedefRemover, null
                        )
                    }
                }
            }
        }
    }
}
