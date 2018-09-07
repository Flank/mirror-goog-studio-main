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

import com.google.common.base.Preconditions.checkNotNull

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.packaging.PackagingFileAction
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.IncrementalFileMergerTransformUtils
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.files.FileCacheByPath
import com.android.builder.merge.DelegateIncrementalFileMergerOutput
import com.android.builder.merge.FilterIncrementalFileMergerInput
import com.android.builder.merge.IncrementalFileMerger
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.builder.merge.IncrementalFileMergerOutput
import com.android.builder.merge.IncrementalFileMergerOutputs
import com.android.builder.merge.IncrementalFileMergerState
import com.android.builder.merge.MergeOutputWriters
import com.android.builder.merge.RenameIncrementalFileMergerInput
import com.android.builder.merge.StreamMergeAlgorithm
import com.android.builder.merge.StreamMergeAlgorithms
import com.android.utils.FileUtils
import com.android.utils.ImmutableCollectors
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Predicate
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors

/**
 * Transform to merge all the Java resources.
 *
 * Based on the value of [.getInputTypes] this will either process native libraries
 * or java resources. While native libraries inside jars are technically java resources, they
 * must be handled separately.
 */
class MergeJavaResourcesDelegate(
    private val packagingOptions: PackagingOptions,
    mergeScopes: Set<in Scope>,
    mergedType: ContentType,
    private val name: String,
    variantScope: VariantScope
) : Transform() {

    private val mergeScopes: Set<in Scope>
    private val mergedType: Set<ContentType>

    private val intermediateDir: File

    private val acceptedPathsPredicate: Predicate<String>
    private val cacheDir: File

    init {
        this.mergeScopes = ImmutableSet.copyOf<Any>(mergeScopes)
        this.mergedType = ImmutableSet.of(mergedType)
        this.intermediateDir = variantScope.getIncrementalDir(
            variantScope.fullVariantName + "-" + name
        )

        cacheDir = File(intermediateDir, "zip-cache")

        if (mergedType === QualifiedContent.DefaultContentType.RESOURCES) {
            acceptedPathsPredicate =
                    { path -> !path.endsWith(SdkConstants.DOT_CLASS) && !path.endsWith(SdkConstants.DOT_NATIVE_LIBS) }
        } else if (mergedType === ExtendedContentType.NATIVE_LIBS) {
            acceptedPathsPredicate = { path ->
                val m = JAR_ABI_PATTERN.matcher(path)

                // if the ABI is accepted, check the 3rd segment
                if (m.matches()) {
                    // remove the beginning of the path (lib/<abi>/)
                    val filename = path.substring(5 + m.group(1).length)
                    // and check the filename
                    return ABI_FILENAME_PATTERN.matcher(filename).matches() ||
                            SdkConstants.FN_GDBSERVER == filename ||
                            SdkConstants.FN_GDB_SETUP == filename
                }

                false
            }
        } else {
            throw UnsupportedOperationException(
                "mergedType param must be RESOURCES or NATIVE_LIBS"
            )
        }
    }

    override fun getName(): String {
        return name
    }

    override fun getInputTypes(): Set<ContentType> {
        return mergedType
    }

    override fun getScopes(): Set<in Scope> {
        return mergeScopes
    }

    override fun getSecondaryDirectoryOutputs(): Collection<File> {
        return ImmutableList.of(cacheDir)
    }

    override fun getParameterInputs(): Map<String, Any> {
        return ImmutableMap.of<String, Any>(
            "exclude", packagingOptions.excludes,
            "pickFirst", packagingOptions.pickFirsts,
            "merge", packagingOptions.merges
        )
    }

    override fun isIncremental(): Boolean {
        return true
    }

    /**
     * Obtains the file where incremental state is saved.
     *
     * @return the file, may not exist
     */
    private fun incrementalStateFile(): File {
        return File(intermediateDir, "merge-state")
    }

    /**
     * Loads the incremental state.
     *
     * @return `null` if the state is not defined
     * @throws IOException failed to load the incremental state
     */
    @Throws(IOException::class)
    private fun loadMergeState(): IncrementalFileMergerState? {
        val incrementalFile = incrementalStateFile()
        if (!incrementalFile.isFile) {
            return null
        }

        try {
            ObjectInputStream(FileInputStream(incrementalFile)).use { i -> return i.readObject() as IncrementalFileMergerState }
        } catch (e: ClassNotFoundException) {
            throw IOException(e)
        }

    }

    /**
     * Save the incremental merge state.
     *
     * @param state the state
     * @throws IOException failed to save the state
     */
    @Throws(IOException::class)
    private fun saveMergeState(state: IncrementalFileMergerState) {
        val incrementalFile = incrementalStateFile()

        FileUtils.mkdirs(incrementalFile.parentFile)
        ObjectOutputStream(FileOutputStream(incrementalFile)).use { o -> o.writeObject(state) }
    }

    @Throws(IOException::class, TransformException::class)
    override fun transform(invocation: TransformInvocation) {
        FileUtils.mkdirs(cacheDir)
        val zipCache = FileCacheByPath(cacheDir)

        val outputProvider = invocation.outputProvider
        checkNotNull<TransformOutputProvider>(
            outputProvider,
            "Missing output object for transform " + getName()
        )

        val packagingOptions = ParsedPackagingOptions(this.packagingOptions)

        var full = false
        var state = loadMergeState()
        if (state == null || !invocation.isIncremental) {
            /*
             * This is a full build.
             */
            state = IncrementalFileMergerState()
            outputProvider!!.deleteAll()
            full = true
        }

        val cacheUpdates = ArrayList<Runnable>()

        val contentMap = HashMap<IncrementalFileMergerInput, QualifiedContent>()
        var inputs: List<IncrementalFileMergerInput> = ArrayList(
            IncrementalFileMergerTransformUtils.toInput(
                invocation,
                zipCache,
                cacheUpdates,
                full,
                contentMap
            )
        )

        /*
         * In an ideal world, we could just send the inputs to the file merger. However, in the
         * real world we live in, things are more complicated :)
         *
         * We need to:
         *
         * 1. We need to bring inputs that refer to the project scope before the other inputs.
         * 2. Prefix libraries that come from directories with "lib/".
         * 3. Filter all inputs to remove anything not accepted by acceptedPathsPredicate neither
         * by packagingOptions.
         */

        // Sort inputs to move project scopes to the start.
        inputs.sort { i0, i1 ->
            val v0 = if (contentMap[i0].getScopes().contains(Scope.PROJECT)) 0 else 1
            val v1 = if (contentMap[i1].getScopes().contains(Scope.PROJECT)) 0 else 1
            v0 - v1
        }

        // Prefix libraries with "lib/" if we're doing libraries.
        assert(mergedType.size == 1)
        val mergedType = this.mergedType.iterator().next()
        if (mergedType === ExtendedContentType.NATIVE_LIBS) {
            inputs = inputs.stream()
                .map { i ->
                    val qc = contentMap[i]
                    if (qc.getFile().isDirectory) {
                        i = RenameIncrementalFileMergerInput(
                            i,
                            { s -> "lib/$s" },
                            { s -> s.substring("lib/".length) })
                        contentMap[i] = qc
                    }

                    i
                }
                .collect<List<IncrementalFileMergerInput>, Any>(Collectors.toList())
        }

        // Filter inputs.
        val inputFilter =
            acceptedPathsPredicate.and { path -> packagingOptions.getAction(path) != PackagingFileAction.EXCLUDE }
        inputs = inputs.stream()
            .map<IncrementalFileMergerInput> { i ->
                val i2 = FilterIncrementalFileMergerInput(i, inputFilter)
                contentMap[i2] = contentMap[i]
                i2
            }
            .collect<List<IncrementalFileMergerInput>, Any>(Collectors.toList())

        /*
         * Create the algorithm used by the merge transform. This algorithm decides on which
         * algorithm to delegate to depending on the packaging option of the path. By default it
         * requires just one file (no merging).
         */
        val mergeTransformAlgorithm = StreamMergeAlgorithms.select { path ->
            val packagingAction = packagingOptions.getAction(path)
            when (packagingAction) {
                PackagingFileAction.EXCLUDE ->
                    // Should have been excluded from the input.
                    throw AssertionError()
                PackagingFileAction.PICK_FIRST -> return@StreamMergeAlgorithms.select StreamMergeAlgorithms . pickFirst ()
                PackagingFileAction.MERGE -> return@StreamMergeAlgorithms.select StreamMergeAlgorithms . concat ()
                PackagingFileAction.NONE -> return@StreamMergeAlgorithms.select StreamMergeAlgorithms . acceptOnlyOne ()
                else -> throw AssertionError()
            }
        }

        /*
         * Create an output that uses the algorithm. This is not the final output because,
         * unfortunately, we still have the complexity of the project scope overriding other scopes
         * to solve.
         *
         * When resources inside a jar file are extracted to a directory, the results may not be
         * expected on Windows if the file names end with "." (bug 65337573), or if there is an
         * uppercase/lowercase conflict. To work around this issue, we copy these resources to a
         * jar file.
         */
        val baseOutput: IncrementalFileMergerOutput
        if (mergedType === QualifiedContent.DefaultContentType.RESOURCES) {
            val outputLocation = outputProvider!!.getContentLocation(
                "resources", outputTypes, scopes, Format.JAR
            )
            baseOutput = IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                mergeTransformAlgorithm, MergeOutputWriters.toZip(outputLocation)
            )
        } else {
            val outputLocation = outputProvider!!.getContentLocation(
                "resources", outputTypes, scopes, Format.DIRECTORY
            )
            baseOutput = IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                mergeTransformAlgorithm,
                MergeOutputWriters.toDirectory(outputLocation)
            )
        }

        /*
         * We need a custom output to handle the case in which the same path appears in multiple
         * inputs and the action is NONE, but only one input is actually PROJECT or FEATURES. In
         * this specific case we will ignore all other inputs.
         */

        val highPriorityInputs = contentMap
            .keys
            .stream()
            .filter { input ->
                containsHighPriorityScope(
                    contentMap[input].getScopes()
                )
            }
            .collect<Set<IncrementalFileMergerInput>, Any>(Collectors.toSet())

        val output = object : DelegateIncrementalFileMergerOutput(baseOutput) {
            override fun create(
                path: String,
                inputs: List<IncrementalFileMergerInput>
            ) {
                super.create(path, filter(path, inputs))
            }

            override fun update(
                path: String,
                prevInputNames: List<String>,
                inputs: List<IncrementalFileMergerInput>
            ) {
                super.update(path, prevInputNames, filter(path, inputs))
            }

            override fun remove(path: String) {
                super.remove(path)
            }

            private fun filter(
                path: String,
                inputs: List<IncrementalFileMergerInput>
            ): ImmutableList<IncrementalFileMergerInput> {
                var inputs = inputs
                val packagingAction = packagingOptions.getAction(path)
                if (packagingAction == PackagingFileAction.NONE && inputs.stream().anyMatch {
                        highPriorityInputs.contains(
                            it
                        )
                    }) {
                    inputs = inputs.stream()
                        .filter { highPriorityInputs.contains(it) }
                        .collect<ImmutableList<IncrementalFileMergerInput>, Builder<IncrementalFileMergerInput>>(
                            ImmutableCollectors.toImmutableList()
                        )
                }

                return ImmutableList.copyOf(inputs)
            }
        }

        state = IncrementalFileMerger.merge(ImmutableList.copyOf(inputs), output, state)
        saveMergeState(state)

        cacheUpdates.forEach(Consumer<Runnable> { it.run() })
    }

    companion object {

        private val JAR_ABI_PATTERN = Pattern.compile("lib/([^/]+)/[^/]+")
        private val ABI_FILENAME_PATTERN = Pattern.compile(".*\\.so")

        private fun containsHighPriorityScope(scopes: Collection<in Scope>): Boolean {
            return scopes.stream()
                .anyMatch { scope -> scope === Scope.PROJECT || scope === InternalScope.FEATURES }
        }
    }
}