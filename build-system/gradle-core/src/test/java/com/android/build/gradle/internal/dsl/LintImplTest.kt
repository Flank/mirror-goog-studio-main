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
import com.google.common.truth.MapSubject
import com.google.common.truth.Truth.assertThat
import groovy.util.Eval
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

class LintImplTest {

    private lateinit var lintWrapper: LintWrapper
    private val severityOverrides: Map<String, LintModelSeverity> get() = (lintWrapper.lint as LintImpl).severityOverridesMap
    private val dslServices: DslServices = createDslServices()

    private fun lint(action: Lint.() -> Unit) = lintWrapper.lint(action)
    private val lint get() = lintWrapper.lint

    interface LintWrapper {
        val lint: Lint
        fun lint(action: Lint.() -> Unit)
    }

    @Before
    fun init() {
        lintWrapper = dslServices.newDecoratedInstance(LintWrapper::class.java, dslServices)
    }

    @Test
    fun testSeverityOverridesMutuallyExclusive() {
        lint {
            enable += "MyCheck"
        }
        assertThat(lint.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lint.disable).named("lint.disable").isEmpty()
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactlyEntries("MyCheck" to DEFAULT_ENABLED)

        lint {
            disable += "MyCheck"
        }
        assertThat(lint.enable).named("lint.enable").isEmpty()
        assertThat(lint.disable).named("lint.disable").containsExactly("MyCheck")
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactlyEntries("MyCheck" to IGNORE)
    }

    @Test
    fun testLocking() {
        lint {
            enable += "MyCheck"
            disable += "OtherCheck"
        }
        assertThat(lint.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lint.disable).named("lint.disable").containsExactly("OtherCheck")
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactlyEntries("MyCheck" to DEFAULT_ENABLED, "OtherCheck" to IGNORE)

        (lint as Lockable).lock()

        val failure = assertFailsWith<RuntimeException> {
            lint {
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

        assertThat(lint.enable).named("lint.enable").containsExactly("MyCheck")
        assertThat(lint.disable).named("lint.disable").containsExactly("OtherCheck")
        assertThat(severityOverrides).named("severityOverrides")
        assertThat(severityOverrides).named("severityOverrides")
            .containsExactlyEntries("MyCheck" to DEFAULT_ENABLED, "OtherCheck" to IGNORE)

    }

    @Test
    fun testGroovyHelpers() {
        Eval.me(
            "android", lintWrapper, """
            android.lint {
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
        """)
        assertThat(lint.checkOnly).named("lint.checkOnly")
            .containsExactly("CheckOnly", "CheckOnly2", "CheckOnly3")

        assertThat(lint.disable).named("lint.disable").containsExactly(
            "Disable", "Disable2", "Disable3",
            "Ignore", "Ignore2", "Ignore3"
        )
        assertThat(lint.enable).named("lint.enable").containsExactly("Enable", "Enable2", "Enable3")
        assertThat(lint.informational).named("lint.informational").containsExactly(
            "Informational",
            "Informational2",
            "Informational3"
        )
        assertThat(lint.warning).named("lint.warning")
            .containsExactly("Warning", "Warning2", "Warning3")
        assertThat(lint.error).named("lint.error").containsExactly("Error", "Error2", "Error3")
        assertThat(lint.fatal).named("lint.fatal").containsExactly("Fatal", "Fatal2", "Fatal3")
    }

    @Test
    fun testGroovyAppend() {
        Eval.me(
            "android", lintWrapper, """
            android.lint {
                checkOnly += ['CheckOnly']
                disable += ['Disable']
                enable += ['Enable']
                informational += ['Informational']
                ignore += ['Ignore']
                warning += ['Warning']
                error += ['Error']
                fatal += ['Fatal']
            }
        """)
        assertThat(lint.checkOnly).named("lint.checkOnly").containsExactly("CheckOnly")
        assertThat(lint.disable).named("lint.disable").containsExactly("Disable", "Ignore")
        assertThat(lint.enable).named("lint.enable").containsExactly("Enable")
        assertThat(lint.informational).named("lint.informational").containsExactly("Informational")
        assertThat(lint.disable).named("lint.ignore").containsExactly("Disable", "Ignore")
        assertThat(lint.warning).named("lint.warning").containsExactly("Warning")
        assertThat(lint.error).named("lint.error").containsExactly("Error")
        assertThat(lint.fatal).named("lint.fatal").containsExactly("Fatal")
        assertThat(severityOverrides).named("severityOverrides").containsExactlyEntries(
            "Disable" to IGNORE,
            "Enable" to DEFAULT_ENABLED,
            "Informational" to INFORMATIONAL,
            "Ignore" to IGNORE,
            "Warning" to WARNING,
            "Error" to ERROR,
            "Fatal" to FATAL,
        )
    }

    @Test
    fun testGroovySetters() {
        Eval.me(
            "android", lintWrapper, """
            android.lint {
                checkOnly = ['CheckOnly']
                disable = ['Disable']
                enable = ['Enable']
                informational = ['Informational']
                ignore = ['Ignore']
                warning = ['Warning']
                error = ['Error']
                fatal = ['Fatal']
            }
        """)
        assertThat(lint.checkOnly).named("lint.checkOnly").containsExactly("CheckOnly")
        assertThat(lint.disable).named("lint.disable")
            .containsExactly("Ignore") // Disable Overwritten by ignore=
        assertThat(lint.enable).named("lint.enable").containsExactly("Enable")
        assertThat(lint.informational).named("lint.informational").containsExactly("Informational")
        assertThat(lint.disable).named("lint.ignore").containsExactly("Ignore")
        assertThat(lint.warning).named("lint.warning").containsExactly("Warning")
        assertThat(lint.error).named("lint.error").containsExactly("Error")
        assertThat(lint.fatal).named("lint.fatal").containsExactly("Fatal")
        assertThat(severityOverrides).named("severityOverrides").containsExactlyEntries(
            "Enable" to DEFAULT_ENABLED,
            "Informational" to INFORMATIONAL,
            "Ignore" to IGNORE,
            "Warning" to WARNING,
            "Error" to ERROR,
            "Fatal" to FATAL,
        )
    }

    @Test
    fun testTextOutput() {
        lint {
            textOutput = File("stdout")
        }
        assertThat(lint.textReport).named("lint.textReport").isTrue()
        assertThat(lint.textOutput?.path).named("lint.textOutput").isEqualTo("stdout")
    }

    @Test
    fun testHtmlOutput() {
        lint {
            htmlOutput = File("lint_report.html")
        }
        assertThat(lint.htmlReport).named("lint.htmlReport").isTrue()
        assertThat(lint.htmlOutput?.path).named("lint.htmlOutput").isEqualTo("lint_report.html")
    }

    @Test
    fun testSarifOutput() {
        lint {
            sarifOutput = File("lint_report.sarif")
        }
        assertThat(lint.sarifReport).named("lint.sarifReport").isTrue()
        assertThat(lint.sarifOutput?.path).named("lint.sarifOutput").isEqualTo("lint_report.sarif")
    }

    @Test
    fun testXmlOutput() {
        lint {
            xmlOutput = File("lint_report.xml")
        }
        assertThat(lint.xmlReport).named("lint.xmlReport").isTrue()
        assertThat(lint.xmlOutput?.path).named("lint.xmlOutput").isEqualTo("lint_report.xml")
    }

    @Test
    fun testBaselineFileSetter() {
        lint {
            baseline = File("lint_baseline.xml")
        }
        assertThat(lint.baseline?.path).named("lint.baselineFile")
            .isEqualTo("lint_baseline.xml")
    }

    @Test
    fun testBaselineAnySetter() {
        Eval.me(
            "android", lintWrapper, """
                android.lint {
                    baseline = "lint_baseline2.xml"
                }
            """)
        assertThat(lint.baseline?.path).named("lint.baselineFile")
            .isEqualTo("lint_baseline2.xml")
    }

    @Test
    fun testLintConfig() {
        lint {
            lintConfig = File("lint_config.xml")
        }
        assertThat(lint.lintConfig?.path).named("lint.lintConfig")
            .isEqualTo("lint_config.xml")
    }

    private fun MapSubject.containsExactlyEntries(vararg pairs: Pair<Any, Any>) {
        containsExactlyEntriesIn(mapOf(*pairs))
    }

}
