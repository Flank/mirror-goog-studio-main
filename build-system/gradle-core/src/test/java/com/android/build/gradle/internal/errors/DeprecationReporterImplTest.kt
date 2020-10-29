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
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Test

class DeprecationReporterImplTest {

    private val issueReporter = FakeSyncIssueReporter()
    private val reporter =
        DeprecationReporterImpl(issueReporter, ProjectOptions(ImmutableMap.of(), FakeProviderFactory(FakeProviderFactory.factory, ImmutableMap.of())), "")

    @After
    fun after() {
        DeprecationReporterImpl.clean()
    }

    @Test
    fun `test experimental options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.BUILD_ONLY_TARGET_ABI, true)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test experimental options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.BUILD_ONLY_TARGET_ABI, false)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.buildOnlyTargetAbi=false' is experimental.\n" +
                    "The current default is 'true'."
        )
    }

    @Test
    fun `test deprecated options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_BUILD_CACHE, true)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test deprecated options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_BUILD_CACHE, false)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.enableBuildCache=false' is deprecated.\n" +
                    "The current default is 'true'.\n" +
                    "It will be removed in version 7.0 of the Android Gradle plugin.\n" +
                    "It does not do anything and AGP is now using Gradle caching."
        )
    }

    @Test
    fun `test removed options, actual value == default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_IN_PROCESS_AAPT2, false)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option 'android.enableAapt2jni' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It has been removed from the current version of the Android Gradle plugin.\n" +
                    "AAPT2 JNI has been removed."
        )
    }

    @Test
    fun `test removed options, actual value != default value`() {
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_IN_PROCESS_AAPT2, true)

        Truth.assertThat(issueReporter.errors).containsExactly(
            "The option 'android.enableAapt2jni' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It has been removed from the current version of the Android Gradle plugin.\n" +
                    "AAPT2 JNI has been removed."
        )
        Truth.assertThat(issueReporter.warnings).isEmpty()
    }

    @Test
    fun `test errors and warnings are reported only once`() {
        // Experimental options
        reporter.reportOptionIssuesIfAny(BooleanOption.BUILD_ONLY_TARGET_ABI, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.BUILD_ONLY_TARGET_ABI, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.BUILD_ONLY_TARGET_ABI, false)

        // Deprecated options
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_BUILD_CACHE, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_BUILD_CACHE, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_BUILD_CACHE, false)

        // Removed options
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_IN_PROCESS_AAPT2, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_IN_PROCESS_AAPT2, true)
        reporter.reportOptionIssuesIfAny(BooleanOption.ENABLE_IN_PROCESS_AAPT2, false)

        Truth.assertThat(issueReporter.errors).containsExactly(
            "The option 'android.enableAapt2jni' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It has been removed from the current version of the Android Gradle plugin.\n" +
                    "AAPT2 JNI has been removed."
        )
        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.buildOnlyTargetAbi=false' is experimental.\n" +
                    "The current default is 'true'.",
            "The option setting 'android.enableBuildCache=false' is deprecated.\n" +
                    "The current default is 'true'.\n" +
                    "It will be removed in version 7.0 of the Android Gradle plugin.\n" +
                    "It does not do anything and AGP is now using Gradle caching.",
            "The option 'android.enableAapt2jni' is deprecated.\n" +
                    "The current default is 'false'.\n" +
                    "It has been removed from the current version of the Android Gradle plugin.\n" +
                    "AAPT2 JNI has been removed."
        )
    }

    @Test
    fun `test deprecated optional option`() {
        reporter.reportOptionIssuesIfAny(OptionalBooleanOption.ENABLE_R8, true)

        Truth.assertThat(issueReporter.errors).isEmpty()
        Truth.assertThat(issueReporter.warnings).containsExactly(
            "The option setting 'android.enableR8=true' is deprecated.\n" +
                    "It will be removed in version 7.0 of the Android Gradle plugin.\n" +
                    "You will no longer be able to disable R8")
    }
}