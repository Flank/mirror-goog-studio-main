/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.infrastructure.Edit.Companion.conflicts
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.util.Disposer
import org.jetbrains.uast.UFile
import java.io.File

/**
 * Special composite [TestMode] which takes a list of source
 * transforming test modes, and attempts to apply as many of them as
 * possible at the same time. If there is a failure, it will then re-run
 * each individual test mode in isolation. This helps speed up the test
 * suite as we add more and more individual test modes since (with the
 * exception of very noisy test modes like the one inserting unnecessary
 * parentheses) often test modes don't overlap and so we don't need to
 * run through all the machinery twice.
 */
internal class UastSourceTransformationTestModeGroup(vararg modes: TestMode) : UastSourceTransformationTestMode(
    "Source code transformations",
    "TestMode.SOURCE_CODE_TRANSFORMATIONS",
    "default"
) {
    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        // This should never be called since we override [processTestFiles]
        // to perform composite editing
        throw IllegalStateException()
    }

    val modes: List<TestMode> = modes.toList()
    private val validModes: MutableList<UastSourceTransformationTestMode> =
        modes.mapNotNull { it as? UastSourceTransformationTestMode }.toMutableList()
    override val folderName: String = "default"
    override val modifiesSources: Boolean = true

    override fun applies(context: TestModeContext): Boolean {
        val applies = validModes.filter { it.applies(context) }
        return if (applies.isEmpty()) {
            false
        } else {
            validModes.clear()
            validModes.addAll(applies)
            true
        }
    }

    override fun partition(context: TestModeContext): List<TestMode> {
        val (contexts, disposable) = parse(
            dir = context.projectFolders.first(),
            sdkHome = context.task.sdkHome
        )
        try {
            return partition(context, contexts)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun partition(
        testContext: TestModeContext,
        contexts: List<JavaContext>
    ): List<SourceTransformationTestMode> {
        // We're assuming two test modes don't cancel each other out, e.g. we shouldn't
        // put both an "add unnecessary parentheses" and a "remove unnecessary parentheses" mode
        // here into the same group)
        var currentModes: MutableList<UastSourceTransformationTestMode> = mutableListOf()
        var currentEditMap: MutableMap<File, Pair<String, MutableList<Edit>>> = mutableMapOf()
        var current = MergedSourceTransformationTestMode(currentModes, currentEditMap)
        val partitions = mutableListOf<MergedSourceTransformationTestMode>()

        val contents = mutableMapOf<File, String>()
        val rootDir = testContext.rootDir
        modeLoop@ for (mode in this.validModes) {
            if (testContext.task.ignoredTestModes.contains(mode)) {
                continue
            }
            val pending = mutableMapOf<File, List<Edit>>()
            val clientData = mutableMapOf<String, Any>()
            for (fileContext in contexts) {
                val file = fileContext.uastFile ?: continue
                val source = file.sourcePsi.text
                val relativePath = fileContext.file.relativeTo(rootDir)
                contents[relativePath] = source
                val edits = mode.transform(source, fileContext, file, clientData)
                if (edits.isNotEmpty()) {
                    edits.sort()

                    if (!ensureConflictFree(mode, fileContext, edits)) {
                        // This individual mode is broken (returning overlapping edits within a single file); skip it
                        continue@modeLoop
                    }

                    pending[relativePath] = edits
                }
            }
            if (pending.isEmpty()) {
                continue
            }
            var conflict = false
            for ((file, edits) in pending) {
                val pair: Pair<String, MutableList<Edit>> = currentEditMap[file]
                    ?: Pair<String, MutableList<Edit>>(contents[file]!!, mutableListOf()).also { currentEditMap[file] = it }
                val currentEdits = pair.second
                if (currentEdits.conflicts(edits)) {
                    conflict = true
                    break
                }
            }
            if (conflict) {
                assert(currentModes.isNotEmpty())
                partitions.add(current)
                currentModes = mutableListOf()
                currentEditMap = mutableMapOf()
                current = MergedSourceTransformationTestMode(currentModes, currentEditMap)

                for ((file, _) in pending) {
                    currentEditMap[file] = Pair(contents[file]!!, mutableListOf())
                }
            }
            for ((file, edits) in pending) {
                // List should already be non-null because of the above iteration
                currentEditMap[file]?.second?.addAll(edits)
            }
            currentModes.add(mode)
        }

        if (currentModes.isNotEmpty()) {
            partitions.add(current)
        }

        // Make sure that all modified files are reset in subsequent partitions!
        val files = mutableMapOf<File, String>()
        for (mode in partitions) {
            val edits: MutableMap<File, Pair<String, MutableList<Edit>>> = mode.edits

            // Make sure all files we've seen so far are present in all subsequent partitions
            for ((file, original) in files) {
                if (!edits.containsKey(file)) {
                    edits[file] = Pair(original, mutableListOf())
                }
            }

            // And any newly added files should be added to the list such that from here
            // on we include it
            for ((file, pair) in edits) {
                if (!files.containsKey(file)) {
                    files[file] = pair.first
                }
            }
        }

        return partitions
    }

    override fun processTestFiles(
        testContext: TestModeContext,
        projectDir: File,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit,
    ): Boolean {
        error("Should not be called")
    }
}
