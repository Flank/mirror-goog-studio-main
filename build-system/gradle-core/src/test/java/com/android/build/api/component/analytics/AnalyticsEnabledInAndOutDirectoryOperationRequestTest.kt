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

package com.android.build.api.component.analytics

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task

import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledInAndOutDirectoryOperationRequestTest {
    @Mock
    lateinit var delegate: InAndOutDirectoryOperationRequest<Task>

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledInAndOutDirectoryOperationRequest<Task>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledInAndOutDirectoryOperationRequest(delegate, stats)
    }

    @Test
    fun toTransform() {
        proxy.toTransform(SingleArtifact.APK)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.TO_TRANSFORM_DIRECTORY_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .toTransform(SingleArtifact.APK)
    }

    @Test
    fun toTransformMany() {
        proxy.toTransformMany(SingleArtifact.APK)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.TO_TRANSFORM_MANY_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .toTransformMany(SingleArtifact.APK)
    }
}
