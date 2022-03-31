/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledSourceDirectoriesTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: SourceDirectories

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledSourceDirectories by lazy {
        object: AnalyticsEnabledSourceDirectories(
            delegate,
            stats,
            FakeObjectFactory.factory) {}
    }

    @Test
    fun testAdd() {
        abstract class CustomTask: Task {
            abstract val output: DirectoryProperty
        }
        @Suppress("UNCHECKED_CAST")
        val taskProvider = Mockito.mock(TaskProvider::class.java) as TaskProvider<CustomTask>

        proxy.addGeneratedSourceDirectory(taskProvider, CustomTask::output)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SOURCES_DIRECTORIES_ADD_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .addGeneratedSourceDirectory(taskProvider, CustomTask::output)
    }

    @Test
    fun testAddSrcDir() {
        proxy.addStaticSourceDirectory("/path/to/directory")

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.SOURCES_DIRECTORIES_SRC_DIR_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .addStaticSourceDirectory("/path/to/directory")
    }
}
