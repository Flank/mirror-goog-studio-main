/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_KTS
import com.android.tools.lint.checks.infrastructure.TestMode.Companion.UI_INJECTION_HOST
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Scope
import java.io.File
import java.util.EnumSet

/**
 * Different types of test execution for lint. See
 * [TestLintTask.testModes]. Similar to an enum, but left open to make
 * it extendable by other test types outside of lint's built in modes.
 * For example, a third party lint check could be run multiple times
 * with different flags set to for example affect how the lint check
 * uses a particular API it depends on.
 */
open class TestMode(
    /**
     * Display name of this test type. Included in diffs if the output
     * varies by test type to annotate the two output versions.
     */
    val description: String,
    /**
     * The qualified name of the canonical field referencing this test
     * mode. This is used by test output to provide guidance on how to
     * run with or without this test mode.
     */
    val fieldName: String
) : Iterable<TestMode> {
    /**
     * Folder name to write the test project into. By passing the same
     * name for multiple test types they can share the same install
     * (since many/most test types don't modify the project structure.)
     */
    open val folderName: String = "default"

    /**
     * Whether the test type is applicable for the given [context]. For
     * example, the [UI_INJECTION_HOST] type will skip projects without
     * Kotlin source files.
     */
    open fun applies(context: TestModeContext) = true

    /**
     * Optional hook to run before running lint on the given list of
     * project directories. The hook can return a state object of some
     * sort, which will be passed back to the [after] hook.
     */
    open fun before(context: TestModeContext): Any? = null

    /**
     * Optional hook to run after the test type has finished, which
     * an perform any appropriate test cleanup. For example, the
     * [UI_INJECTION_HOST] test type will set a global flag to change
     * the behavior of UAST, so this method lets the test type clean
     * this up.
     */
    open fun after(context: TestModeContext) {
    }

    /**
     * Optional hook to register to be notified of events triggered by
     * lint. It will pass back the state object optionally created by
     * the [before] hook.
     */
    open val eventListener: ((TestModeContext, LintListener.EventType, Any?) -> Unit)? = null

    /**
     * Custom explanation to show when the output is different than a
     * previous test type.
     */
    open val diffExplanation: String? = null

    override fun iterator(): Iterator<TestMode> {
        return values().iterator()
    }

    override fun toString(): String = description

    companion object {
        fun classOnly(set: EnumSet<Scope>): Boolean {
            return set.all { it == Scope.CLASS_FILE || it == Scope.JAVA_LIBRARIES || it == Scope.ALL_CLASS_FILES }
        }

        fun Implementation.classOnly(): Boolean {
            return classOnly(scope) && analysisScopes.all { classOnly(it) }
        }

        fun deleteCompiledSources(
            projects: Collection<ProjectDescription>,
            context: TestModeContext,
            deleteSourceFiles: Boolean = false,
            deleteBinaryFiles: Boolean = false
        ) {
            // Delete sources for any compiled files since when analyzing a project
            // you can only see the local sources.
            for (project in projects) {
                for (file in project.files) {
                    // TODO: Consider whether I need to remove sources from within jars too?
                    // Check whether resolve finds them first.

                    if (file !is CompiledSourceFile) {
                        continue
                    }

                    // If we're testing a check that only analyzes bytecode, don't delete sources
                    // or bytecode
                    if (context.task.issues.all { it.implementation.classOnly() }) {
                        return
                    }

                    if (deleteSourceFiles && !file.targetRelativePath.endsWith(DOT_JAR)) {
                        for (dir in context.projectFolders) {
                            val source = File(dir, file.source.targetPath)
                            if (source.exists()) {
                                source.delete()
                                break
                            }
                        }
                    }
                    if (deleteBinaryFiles) {
                        for (classTestFile in file.classFiles) {
                            for (dir in context.projectFolders) {
                                val classFile = File(dir, classTestFile.targetPath)
                                if (classFile.exists()) {
                                    classFile.delete()
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }

        /** The default type of lint execution. */
        @JvmField
        val DEFAULT = TestMode(description = "Default", "TestMode.DEFAULT")

        /** Run lint with UI injection host mode turned on. */
        @JvmField
        val UI_INJECTION_HOST = object : TestMode(
            "UInjectionHost Enabled",
            "TestMode.UI_INJECTION_HOST"
        ) {
            override fun applies(context: TestModeContext): Boolean {
                return context.projects.any { project ->
                    project.files.any {
                        it.targetRelativePath.endsWith(DOT_KT) ||
                            it.targetRelativePath.endsWith(DOT_KTS)
                    }
                }
            }

            override fun before(context: TestModeContext): Any {
                return TestLintTask.setForceUiInjection(true)
            }

            override fun after(context: TestModeContext) {
                TestLintTask.setForceUiInjection(false)
            }

            override val diffExplanation =
                """
                The unit test results differ based on whether
                `kotlin.uast.force.uinjectionhost` is on or off. Make sure your
                detector correctly handles strings in Kotlin; soon all String
                `ULiteralExpression` elements will be wrapped in a `UPolyadicExpression`.
                Lint now runs the tests twice, in both modes, and checks that
                the results are identical.

                Alternatively, if this difference is expected, you can set the
                `testModes(...)` to include only one of these two, or turn off
                the equality check altogether via `.expectIdenticalTestModeOutput(false)`.
                You can then check each output by passing in a `testMode` parameter
                to `expect`(...).
                """.trimIndent()
        }

        @JvmField
        val BYTECODE_ONLY = object : TestMode(
            "Bytecode Only",
            "TestMode.BYTECODE_ONLY"
        ) {
            override val folderName: String = "bytecode"

            override fun applies(context: TestModeContext): Boolean {
                return context.projects.any {
                    it.files.any { file ->
                        file is CompiledSourceFile && file.type == CompiledSourceFile.Type.SOURCE_AND_BYTECODE
                    }
                }
            }

            override fun before(context: TestModeContext): Any? {
                deleteCompiledSources(context.projects, context, deleteSourceFiles = true)
                return null
            }

            override val diffExplanation: String =
                """
                The unit test was re-run with only the bytecode from the `compiled()` test files,
                not the sources, and the output did not match. This is sometimes expected (since
                it's common to include source details in error messages), and in those cases, you
                can set the `testModes(...)` to include only one of these two, or turn off
                the equality check altogether via `.expectIdenticalTestModeOutput(false)`.
                You can then check each output by passing in a `testMode` parameter
                to `expect`(...).
                """.trimIndent()
        }

        @JvmField
        val SOURCE_ONLY = object : TestMode(
            "Source Only",
            "TestMode.SOURCE_ONLY"
        ) {
            override val folderName: String = "source"

            override fun applies(context: TestModeContext): Boolean {
                // Same check as for BYTECODE_ONLY: Do we have at least one compiled file with
                // both source and bytecode?
                return BYTECODE_ONLY.applies(context)
            }

            override fun before(context: TestModeContext): Any? {
                deleteCompiledSources(context.projects, context, deleteBinaryFiles = true)
                return null
            }

            override val diffExplanation: String =
                """
                The unit test was re-run with only the source files from the `compiled()` test files,
                not the bytecode, and the output did not match. This is sometimes expected,
                and in those cases, you can set the `testModes(...)` to include only one of these two,
                or turn off the equality check altogether via `.expectIdenticalTestModeOutput(false)`.
                You can then check each output by passing in a `testMode` parameter
                to `expect`(...).
                """.trimIndent()
        }

        @JvmField
        val RESOURCE_REPOSITORIES = object : TestMode(
            "AGP Resource Repository",
            "TestMode.RESOURCE_REPOSITORIES"
        ) {

            override fun applies(context: TestModeContext): Boolean {
                return context.task.requestedResourceRepository
            }

            override fun before(context: TestModeContext): Any? {
                context.task.forceAgpResourceRepository = true
                return null
            }

            override fun after(context: TestModeContext) {
                context.task.forceAgpResourceRepository = false
            }

            override val diffExplanation: String =
                """
                The unit test output varies whe using lint's resource
                repository (optimized for lint's use-cases) and the AGP
                resource repository. This is a bug in lint. Please report it.
                """.trimIndent()
        }

        /**
         * Provisional testing support which attempts to find errors
         * in partial analysis handling from detectors. For single
         * project tests, it will first drop the minSdkVersion down to
         * 1 and run lint in analysis-mode only; it will then revert
         * the minSdkVersion and run it in merge mode. This will not
         * only find lint checks which are incorrectly deferring
         * minSdkVersion calculations to the merge stage; it will also
         * cause any detectors which are calling illegal methods during
         * analysis mode (such as getMainProject) to be caught. (Note
         * that this isn't limited to minSdkVersion, it will also modify
         * targetSdkVersion, and potentially additional elements in
         * the manifest, such as moving all permissions into the main
         * module, though that is not yet implemented.)
         *
         * For multi-module projects it will pick the "main" module
         * (e.g. app), and then run analysis-only on all the modules,
         * then merge on the main module.
         */
        @JvmField
        val PARTIAL: TestMode = PartialTestMode()

        /** Returns all default included test modes. */
        @JvmStatic
        fun values(): List<TestMode> = listOf(
            DEFAULT,
            UI_INJECTION_HOST,
            RESOURCE_REPOSITORIES,
            PARTIAL,
            BYTECODE_ONLY,
            SOURCE_ONLY
        )
    }

    /**
     * State passed to test modes. This is encapsulated in a separate
     * object such that it can vary over time without breaking any
     * third party test modes which extend [TestMode] and implement its
     * methods.
     */
    class TestModeContext(
        val task: TestLintTask,
        val projects: List<ProjectDescription>,
        val projectFolders: List<File>,
        val clientState: Any?,
        val driver: LintDriver? = null,
        val lintContext: Context? = null
    )
}
