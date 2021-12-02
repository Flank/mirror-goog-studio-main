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

import com.android.tools.lint.detector.api.JavaContext
import org.jetbrains.uast.UFile
import java.io.File

/**
 * An individual [TestMode] which performs a pre-computed set of edits
 * on a set of source files. These are merged from multiple individual
 * modes.
 */
internal class MergedSourceTransformationTestMode(
    internal val modes: List<TestMode>,
    internal val edits: MutableMap<File, Pair<String, MutableList<Edit>>>
) : UastSourceTransformationTestMode(
    "Merged Source code transformations",
    "TestMode.SOURCE_CODE_TRANSFORMATIONS",
    "source-transformations"
) {
    override val description: String
        get() = modes.joinToString { it.description }

    override val fieldName: String
        get() = modes.joinToString { it.fieldName }

    override val folderName: String
        get() = modes.joinToString("-") { it.folderName }

    override val modifiesSources: Boolean = true

    private fun initializeSources(testContext: TestModeContext) {
        for ((file, contents) in edits) {
            val (original, edits) = contents
            val edited = Edit.performEdits(original, edits)
            val target = if (file.isAbsolute) file else File(testContext.rootDir, file.path)
            assert(target.isFile) { target.path }
            target.writeText(edited)
        }
    }

    override fun sameOutput(expected: String, actual: String, type: OutputKind): Boolean {
        return modes.any { mode -> mode.sameOutput(expected, actual, type) }
    }

    override fun processTestFiles(
        testContext: TestModeContext,
        projectDir: File,
        sdkHome: File?,
        changeCallback: (JavaContext, String) -> Unit,
    ): Boolean {
        initializeSources(testContext)
        return true
    }

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

    override fun toString(): String {
        if (modes.size == 1) return modes[0].toString()
        return MergedSourceTransformationTestMode::class.java.simpleName + ":" + modes.joinToString()
    }
}
