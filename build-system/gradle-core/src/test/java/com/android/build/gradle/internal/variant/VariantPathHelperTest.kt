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

package com.android.build.gradle.internal.variant

import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class VariantPathHelperTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)

    val project: Project = ProjectBuilder.builder().build()

    val buildDirectory: DirectoryProperty by lazy {
        project.layout.buildDirectory
    }

    @Mock
    lateinit var variantDslInfo: VariantDslInfo

    @Mock
    lateinit var dslServices: DslServices

    @Mock
    lateinit var projectOptions: ProjectOptions

    @Before
    fun setup() {
        `when`(dslServices.projectOptions).thenReturn(projectOptions)
        `when`(variantDslInfo.dirName).thenReturn("apk_location")
    }

    @Test
    fun testCustomAbiBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        doReturn("x86").`when`(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("intermediates")
    }

    @Test
    fun testCustomDensityBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        doReturn("xxdpi").`when`(projectOptions).get(StringOption.IDE_BUILD_TARGET_DENSITY)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("intermediates")
    }

    @Test
    fun testCustomAPIBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        doReturn(21).`when`(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("intermediates")
    }

    @Test
    fun testIdeBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        // necessary, otherwise mockito will return 0.
        doReturn(null).`when`(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        doReturn(true).`when`(projectOptions).get(BooleanOption.IDE_INVOKED_FROM_IDE)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("outputs")
    }

    @Test
    fun testNormalBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        // necessary, otherwise mockito will return 0.
        doReturn(null).`when`(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("outputs")
    }
}
