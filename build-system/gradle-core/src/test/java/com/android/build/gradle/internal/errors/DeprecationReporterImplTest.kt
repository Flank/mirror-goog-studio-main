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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.Version
import com.android.build.gradle.options.parseBoolean
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class DeprecationReporterImplTest {

    enum class FakeOption(
        override val propertyName: String,
        override val defaultValue: Boolean?,
        override val status: Option.Status
    ) : Option<Boolean?> {
        EXPERIMENTAL("android.experimental.option", false, Option.Status.EXPERIMENTAL),
        DEPRECATED(
            "android.deprecated.option",
            false,
            Option.Status.Deprecated(DeprecationReporter.DeprecationTarget.VERSION_7_0)
        ),
        DEPRECATED_OPTIONAL(
            "android.deprecated.optional.option",
            null,
            Option.Status.Deprecated(DeprecationReporter.DeprecationTarget.VERSION_7_0)
        ),
        REMOVED(
            "android.removed.option",
            false,
            Option.Status.Removed(Version.VERSION_7_0, "Extra message.")
        ),
        ;
        override fun parse(value: Any): Boolean = parseBoolean(propertyName, value)
    }

    private val issueReporter = FakeSyncIssueReporter()
    private val reporter =
        DeprecationReporterImpl(issueReporter, ProjectOptions(ImmutableMap.of(), FakeProviderFactory(FakeProviderFactory.factory, ImmutableMap.of())), "")

    @After
    fun after() {
        DeprecationReporterImpl.clean()
    }

    @Test
    fun `test experimental options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, false)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test experimental options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, true)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.experimental.option=true' is experimental.\n" +
                    "The current default is 'false'."
        )
    }

    @Test
    fun `test deprecated options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, false)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test deprecated options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, true)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.deprecated.option=true' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It will be removed in version 7.0 of the Android Gradle plugin."
        )
    }

    @Test
    fun `test removed options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, false)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).containsExactly(
            "The option 'android.removed.option' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It was removed in version 7.0 of the Android Gradle plugin.\n" +
                    "Extra message."
        )
    }

    @Test
    fun `test removed options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, true)

        assertThat(issueReporter.errors).containsExactly(
            "The option 'android.removed.option' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It was removed in version 7.0 of the Android Gradle plugin.\n" +
                    "Extra message."
        )
        assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test errors and warnings are reported only once`() {
        // Experimental options
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, true)
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, true)
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, false)
        reporter.reportOptionIssuesIfAny(FakeOption.EXPERIMENTAL, false)

        // Deprecated options
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, true)
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, true)
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, false)
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED, false)

        // Removed options
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, true)
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, true)
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, false)
        reporter.reportOptionIssuesIfAny(FakeOption.REMOVED, false)

        assertThat(issueReporter.errors).containsExactly(
            """
            The option 'android.removed.option' is deprecated.
            The current default is 'false'.
            It was removed in version 7.0 of the Android Gradle plugin.
            Extra message.
            """.trimIndent()
        )
        assertThat(issueReporter.warnings).containsExactly(
            """
                The option setting 'android.experimental.option=true' is experimental.
                The current default is 'false'.
            """.trimIndent(),
            """
                The option setting 'android.deprecated.option=true' is deprecated.
                The current default is 'false'.
                It will be removed in version 7.0 of the Android Gradle plugin.
            """.trimIndent(),
            """
                The option 'android.removed.option' is deprecated.
                The current default is 'false'.
                It was removed in version 7.0 of the Android Gradle plugin.
                Extra message.
            """.trimIndent()
        )
    }

    @Test
    fun `test deprecated optional option`() {
        reporter.reportOptionIssuesIfAny(FakeOption.DEPRECATED_OPTIONAL, true)

        assertThat(issueReporter.errors).isEmpty()
        assertThat(issueReporter.warnings).containsExactly(
            """
                The option setting 'android.deprecated.optional.option=true' is deprecated.
                It will be removed in version 7.0 of the Android Gradle plugin.
            """.trimIndent()
        )
    }
}
