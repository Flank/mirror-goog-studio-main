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

import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget.CONFIG_NAME
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Test

class DeprecationReporterImplTest {

    private val issueReporter = FakeEvalIssueReporter()
    private val reporter = DeprecationReporterImpl(
        issueReporter, ProjectOptions(
            ImmutableMap.of()
        ), ""
    )

    @After
    fun after() {
        DeprecationReporterImpl.clean()
    }

    @Test
    fun `test single output for deprecated options`() {
        reporter.reportDeprecatedOption(BooleanOption.BUILD_ONLY_TARGET_ABI.name, "foo", CONFIG_NAME)
        reporter.reportDeprecatedOption(BooleanOption.BUILD_ONLY_TARGET_ABI.name, "foo", CONFIG_NAME)
        reporter.reportDeprecatedOption(BooleanOption.BUILD_ONLY_TARGET_ABI.name, "bar", CONFIG_NAME)

        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option 'BUILD_ONLY_TARGET_ABI' is deprecated and should not be used anymore.\n" +
                    "Use 'BUILD_ONLY_TARGET_ABI=foo' to remove this warning.\n" +
                    "It will be removed soon.",
            "The option 'BUILD_ONLY_TARGET_ABI' is deprecated and should not be used anymore.\n" +
                    "Use 'BUILD_ONLY_TARGET_ABI=bar' to remove this warning.\n" +
                    "It will be removed soon."
        )
    }

    @Test
    fun `test single output for experimental options`() {
        reporter.reportExperimentalOption(BooleanOption.BUILD_ONLY_TARGET_ABI, "foo")
        reporter.reportExperimentalOption(BooleanOption.BUILD_ONLY_TARGET_ABI, "foo")
        reporter.reportExperimentalOption(BooleanOption.BUILD_ONLY_TARGET_ABI, "bar")

        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.buildOnlyTargetAbi=foo' is experimental and unsupported.\n" +
                    "The current default is 'true'.\n",
            "The option setting 'android.buildOnlyTargetAbi=bar' is experimental and unsupported.\n" +
                    "The current default is 'true'.\n")

    }
}