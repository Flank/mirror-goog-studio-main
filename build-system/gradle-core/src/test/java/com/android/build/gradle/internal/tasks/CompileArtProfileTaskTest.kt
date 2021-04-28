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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.tasks.CompileArtProfileTask.CompileArtProfileWorkAction
import com.android.sdklib.AndroidVersion
import com.android.tools.profgen.ArtProfileSerializer
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class CompileArtProfileTaskTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var parameters: CompileArtProfileWorkAction.Parameters

    @Test
    fun testSerializerBeforeN() {
        Mockito.`when`(parameters.minSdkVersion).thenReturn(FakeGradleProperty(
                AndroidVersion.VersionCodes.M
        ))
        Truth.assertThat(CompileArtProfileWorkAction.getArtProfileSerializer(parameters)).isNull()
    }

    @Test
    fun testSerializerAtN() {
        Mockito.`when`(parameters.minSdkVersion).thenReturn(FakeGradleProperty(
                AndroidVersion.VersionCodes.N
        ))
        Truth.assertThat(CompileArtProfileWorkAction.getArtProfileSerializer(parameters)).isEqualTo(
                ArtProfileSerializer.V0_0_1_N
        )
    }

    @Test
    fun testSerializerAtO() {
        Mockito.`when`(parameters.minSdkVersion).thenReturn(FakeGradleProperty(
                AndroidVersion.VersionCodes.O
        ))
        Truth.assertThat(CompileArtProfileWorkAction.getArtProfileSerializer(parameters)).isEqualTo(
                ArtProfileSerializer.V0_0_5_O
        )
    }

    @Test
    fun testSerializerAtP() {
        Mockito.`when`(parameters.minSdkVersion).thenReturn(FakeGradleProperty(
                AndroidVersion.VersionCodes.P
        ))
        Truth.assertThat(CompileArtProfileWorkAction.getArtProfileSerializer(parameters)).isEqualTo(
                ArtProfileSerializer.V0_1_0_P
        )
    }
    @Test
    fun testSerializerAfterP() {
        Mockito.`when`(parameters.minSdkVersion).thenReturn(FakeGradleProperty(
                AndroidVersion.VersionCodes.Q
        ))
        Truth.assertThat(CompileArtProfileWorkAction.getArtProfileSerializer(parameters)).isEqualTo(
                ArtProfileSerializer.V0_1_0_P
        )
    }
}
