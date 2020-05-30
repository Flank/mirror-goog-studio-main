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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.model.SyncIssue.Companion.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

class IncompatibleProjectOptionsReporterTest {

    private val reporter = FakeSyncIssueReporter()

    @Test
    fun `test AndroidX enabled Jetifier enabled, expect success`() {
        val gradleProperties = ImmutableMap.of<String, Any>(
            BooleanOption.USE_ANDROID_X.propertyName, TRUE,
            BooleanOption.ENABLE_JETIFIER.propertyName, TRUE
        )
        IncompatibleProjectOptionsReporter.check(
            ProjectOptions(
                @Suppress("RemoveExplicitTypeArguments")
                ImmutableMap.of(),
                FakeProviderFactory(FakeProviderFactory.factory, gradleProperties)
            ),
            reporter
        )
        assertThat(reporter.errors).isEmpty()
        assertThat(reporter.warnings).isEmpty()
    }

    @Test
    fun `test AndroidX disabled Jetifier enabled, expect failure`() {
        val gradleProperties = ImmutableMap.of<String, Any>(
            BooleanOption.USE_ANDROID_X.propertyName, FALSE,
            BooleanOption.ENABLE_JETIFIER.propertyName, TRUE
        )
        IncompatibleProjectOptionsReporter.check(
            ProjectOptions(
                @Suppress("RemoveExplicitTypeArguments")
                ImmutableMap.of(),
                FakeProviderFactory(FakeProviderFactory.factory, gradleProperties)
            ),
            reporter
        )
        assertThat(reporter.errors).containsExactly(
            "AndroidX must be enabled when Jetifier is enabled. To resolve, set" +
                    " ${BooleanOption.USE_ANDROID_X.propertyName}=true" +
                    " in your gradle.properties file."
        )
        assertThat(reporter.warnings).isEmpty()
        assertThat(reporter.syncIssues).hasSize(1)
        assertThat(reporter.syncIssues[0].type).isEqualTo(TYPE_ANDROID_X_PROPERTY_NOT_ENABLED)
    }
}