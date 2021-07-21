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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.tools.lint.model.LintModelSeverity
import com.android.tools.lint.model.LintModelSeverity.DEFAULT_ENABLED
import com.android.tools.lint.model.LintModelSeverity.ERROR
import com.android.tools.lint.model.LintModelSeverity.FATAL
import com.android.tools.lint.model.LintModelSeverity.IGNORE
import com.android.tools.lint.model.LintModelSeverity.INFORMATIONAL
import com.android.tools.lint.model.LintModelSeverity.WARNING
import com.google.common.truth.Truth.assertThat
import groovy.util.Eval
import org.gradle.api.Action
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.inject.Inject
import kotlin.test.assertFailsWith

/**
 * Test for the semantic of the legacy [com.android.build.api.dsl.LintOptions] block,
 * which is now backed by the new [com.android.build.api.dsl.Lint].
 *
 * This looks similar to `LintImplTest`, but as they have some differences and can evolve separately
 * they are tested separately
 *
 */
class LintOptionsTest {

    // The underlying new Lint block (where everything is stored)
    private lateinit var wrappedLint: Lint
    // A fake 'android' for groovy tests.
    private lateinit var lintWrapper: LintWrapper
    private val severityOverrides: Map<String, LintModelSeverity> get() = (lintWrapper.lintOptions as LintOptions).severityOverridesMap
    private val dslServices: DslServices = createDslServices()

    @Suppress("DEPRECATION")
    private fun lintOptions(action: com.android.build.api.dsl.LintOptions.() -> Unit) = action.invoke(lintOptions)
    private val lintOptions get() = lintWrapper.lintOptions

    @Suppress("DEPRECATION")
    open class LintWrapper @Inject constructor(val lintOptions: com.android.build.api.dsl.LintOptions) {
        fun lintOptions(action: Action<com.android.build.api.dsl.LintOptions>) {
            action.execute(lintOptions)
        }
    }

    @Before
    fun init() {
        // An approximation of the DSL instantiation process
        wrappedLint = dslServices.newDecoratedInstance(LintImpl::class.java, dslServices)
        val lintOptions = dslServices.newInstance(LintOptions::class.java, dslServices, wrappedLint)
        lintWrapper = dslServices.newInstance(LintWrapper::class.java, lintOptions)
    }

    @Test
    fun testSeverityOverridesMutuallyExclusive() {
        lintOptions {
            enable += "MyCheck"
        }
        assertThat(lintOptions.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lintOptions.disable).named("lint.disable").isEmpty()
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactly("MyCheck", DEFAULT_ENABLED)

        lintOptions {
            disable += "MyCheck"
        }
        assertThat(lintOptions.enable).named("lint.enable").isEmpty()
        assertThat(lintOptions.disable).named("lint.disable").containsExactly("MyCheck")
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactly("MyCheck", IGNORE)
    }

    @Test
    fun testLocking() {
        lintOptions {
            enable += "MyCheck"
        }
        assertThat(lintOptions.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lintOptions.disable).named("lint.disable").isEmpty()
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactly("MyCheck", DEFAULT_ENABLED)

        (wrappedLint as Lockable).lock()

        val failure = assertFailsWith<RuntimeException> {
            lintOptions {
                disable += "MyCheck"
            }
        }

        assertThat(failure).named("failure").hasMessageThat().isEqualTo(
            """
            It is too late to modify disable
            It has already been read to configure this project.
            Consider either moving this call to be during evaluation,
            or using the variant API.
            """.trimIndent()
        )

        assertThat(lintOptions.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lintOptions.disable).named("lint.disable").isEmpty()
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactly("MyCheck", DEFAULT_ENABLED)

    }

    @Test
    fun testGroovyHelpers() {
        Eval.me(
            "android", lintWrapper, """
            android.lintOptions {
                checkOnly 'CheckOnly'
                checkOnly 'CheckOnly2', 'CheckOnly3'
                disable 'Disable'
                disable 'Disable2', 'Disable3'
                enable 'Enable'
                enable 'Enable2', 'Enable3'
                informational 'Informational'
                informational 'Informational2', 'Informational3'
                ignore 'Ignore'
                ignore 'Ignore2', 'Ignore3'
                warning 'Warning'
                warning 'Warning2', 'Warning3'
                error 'Error'
                error 'Error2', 'Error3'
                fatal 'Fatal'
                fatal 'Fatal2', 'Fatal3'
            }
        """
        )
        assertThat(lintOptions.checkOnly).named("lint.checkOnly").containsExactly("CheckOnly", "CheckOnly2", "CheckOnly3")

        assertThat(lintOptions.disable).named("lint.disable").containsExactly(
            "Disable", "Disable2", "Disable3",
            "Ignore", "Ignore2", "Ignore3"
        )
        assertThat(lintOptions.enable).named("lint.enable").containsExactly("Enable", "Enable2", "Enable3")
        assertThat(lintOptions.informational).named("lint.informational").containsExactly(
            "Informational",
            "Informational2",
            "Informational3"
        )
        assertThat(lintOptions.warning).named("lint.warning")
            .containsExactly("Warning", "Warning2", "Warning3")
        assertThat(lintOptions.error).named("lint.error").containsExactly("Error", "Error2", "Error3")
        assertThat(lintOptions.fatal).named("lint.fatal").containsExactly("Fatal", "Fatal2", "Fatal3")
    }

    @Test
    fun testGroovyAppend() {
        lintOptions {
            ignore("Ignore")
        }
        Eval.me(
            "android", lintWrapper, """
            android.lintOptions {
                disable += ['Disable']
                enable += ['Enable']
            }
        """
        )
        assertThat(lintOptions.disable).named("lint.disable").containsExactly("Disable", "Ignore")
        assertThat(lintOptions.enable).named("lint.enable").containsExactly("Enable")
        assertThat(severityOverrides).named("severityOverrides").containsExactlyEntriesIn(
            mapOf(
                "Ignore" to IGNORE,
                "Disable" to IGNORE,
                "Enable" to DEFAULT_ENABLED,
            )
        )
    }

    @Test
    fun testTextOutput() {
        lintOptions {
            textOutput = File("stdout")
        }
        assertThat(lintOptions.textReport).named("lint.textReport").isTrue()
        assertThat(lintOptions.textOutput?.path).named("lint.textOutput").isEqualTo("stdout")
    }

    @Test
    fun testHtmlOutput() {
        lintOptions {
            htmlOutput = File("stdout")
        }
        assertThat(lintOptions.htmlReport).named("lint.htmlReport").isTrue()
        assertThat(lintOptions.htmlOutput?.path).named("lint.htmlOutput").isEqualTo("stdout")
    }

    @Test
    fun testSarifOutput() {
        lintOptions {
            sarifOutput = File("stdout")
        }
        assertThat(lintOptions.sarifReport).named("lint.sarifReport").isTrue()
        assertThat(lintOptions.sarifOutput?.path).named("lint.sarifOutput").isEqualTo("stdout")
    }

    @Test
    fun testXmlOutput() {
        lintOptions {
            xmlOutput = File("stdout")
        }
        assertThat(lintOptions.xmlReport).named("lint.xmlReport").isTrue()
        assertThat(lintOptions.xmlOutput?.path).named("lint.xmlOutput").isEqualTo("stdout")
    }

    @Test
    fun testBaselineFileSetter() {
        lintOptions {
            baselineFile = File("lint_baseline.xml")
        }
        assertThat(lintOptions.baselineFile?.path).named("lint.baselineFile")
            .isEqualTo("lint_baseline.xml")
    }

    @Test
    fun testBaselineFileHelper() {
        lintOptions { this as LintOptions
            baseline(File("a"))
        }
        Eval.me(
            "android", lintWrapper, """
                File file = new File("lint_baseline2.xml")
                android.lintOptions {
                    baseline file
                }
            """)
        assertThat(lintOptions.baselineFile?.path).named("lint.baselineFile")
            .isEqualTo("lint_baseline2.xml")
        Eval.me(
            "android", lintWrapper, """
                android.lintOptions {
                    baseline 'lint_baseline3.xml'
                }
            """)

        assertThat(lintOptions.baselineFile?.path).named("lint.baselineFile")
            .isEqualTo("lint_baseline3.xml")
    }

    @Test
    fun testLintConfig() {
        lintOptions {
            lintConfig = File("lint_config.xml")
        }
        assertThat(lintOptions.lintConfig?.path).named("lint.lintConfig")
            .isEqualTo("lint_config.xml")
    }


    @Suppress("DEPRECATION")
    companion object {
        // Helpers for the legacy lintOptions block where the severity overrides were not exposed as collections
        val com.android.build.api.dsl.LintOptions.informational get() = (this as LintOptions).severityOverridesMap.filterValues { it == INFORMATIONAL }.keys
        val com.android.build.api.dsl.LintOptions.warning get() = (this as LintOptions).severityOverridesMap.filterValues { it == WARNING }.keys
        val com.android.build.api.dsl.LintOptions.error get() = (this as LintOptions).severityOverridesMap.filterValues { it == ERROR }.keys
        val com.android.build.api.dsl.LintOptions.fatal get() = (this as LintOptions).severityOverridesMap.filterValues { it == FATAL }.keys

    }
}
