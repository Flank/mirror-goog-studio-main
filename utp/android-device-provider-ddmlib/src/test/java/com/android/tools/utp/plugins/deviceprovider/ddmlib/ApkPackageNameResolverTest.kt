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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.lib.process.Handle
import com.google.testing.platform.lib.process.Subprocess
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.isNull
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness

/**
 * Unit tests for [ApkPackageNameResolver].
 */
class ApkPackageNameResolverTest {
    @get:Rule val mockitoJUnitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    private lateinit var mockSubprocessComponent: SubprocessComponent
    @Mock
    private lateinit var mockSubprocess: Subprocess

    @Before
    fun setUpMocks() {
        `when`(mockSubprocessComponent.subprocess()).thenReturn(mockSubprocess)
    }

    private fun getPackageNameFromApk(
        aaptOutput: String
    ): String? {
        `when`(mockSubprocess.executeAsync(
            eq(listOf("aaptPath", "dump", "badging", "apkPath")),
            anyMap(),
            any(),
            isNull()
        )).then {
            val stdoutProcessor: (String) -> Unit = it.getArgument(2)
            stdoutProcessor(aaptOutput)
            mock<Handle>()
        }
        val resolver = ApkPackageNameResolver("aaptPath", mockSubprocessComponent)
        return resolver.getPackageNameFromApk("apkPath")
    }

    @Test
    fun packageNameFound() {
        val packageName = getPackageNameFromApk("""
            package: name='com.example.myapplication' versionCode='1' versionName='1.0' compileSdkVersion='30' compileSdkVersionCodename='11'
        """.trimIndent())

        assertThat(packageName).isEqualTo("com.example.myapplication")
    }

    @Test
    fun packageNameNotFound() {
        val packageName = getPackageNameFromApk("")

        assertThat(packageName).isNull()
    }
}
