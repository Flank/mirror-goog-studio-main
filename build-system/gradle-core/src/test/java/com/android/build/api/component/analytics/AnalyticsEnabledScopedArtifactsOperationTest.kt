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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifactsOperation
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Rule
import org.junit.Test
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AnalyticsEnabledScopedArtifactsOperationTest {

    abstract class FakeTask: DefaultTask() {
        abstract val inputJars: ListProperty<RegularFile>
        abstract val inputDirectories: ListProperty<Directory>
        abstract val output: RegularFileProperty
    }

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var delegate: ScopedArtifactsOperation<FakeTask>

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledScopedArtifactsOperation<FakeTask> by lazy {
        AnalyticsEnabledScopedArtifactsOperation<FakeTask>(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun testToGet() {

        proxy.toGet(
            ScopedArtifact.CLASSES,
            FakeTask::inputJars,
            FakeTask::inputDirectories,
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_GET_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).toGet(
            ScopedArtifact.CLASSES,
            FakeTask::inputJars,
            FakeTask::inputDirectories,
        )
    }

    @Test
    fun testToApAlpend() {

        proxy.toAppend(
            ScopedArtifact.CLASSES,
            FakeTask::output
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.SCOPED_ARTIFACTS_APPEND_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).toAppend(
            ScopedArtifact.CLASSES,
            FakeTask::output
        )
    }

    @Test
    fun testToReplace() {

        proxy.toReplace(
            ScopedArtifact.CLASSES,
            FakeTask::output
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_REPLACE_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).toReplace(
            ScopedArtifact.CLASSES,
            FakeTask::output
        )
    }

    @Test
    fun testToTransform() {

        proxy.toTransform(
            ScopedArtifact.CLASSES,
            FakeTask::inputJars,
            FakeTask::inputDirectories,
            FakeTask::output
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.SCOPED_ARTIFACTS_TO_TRANSFORM_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).toTransform(
            ScopedArtifact.CLASSES,
            FakeTask::inputJars,
            FakeTask::inputDirectories,
            FakeTask::output
        )
    }
}
