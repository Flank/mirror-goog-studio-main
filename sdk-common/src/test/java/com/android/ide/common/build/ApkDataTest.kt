/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.build

import com.android.build.VariantOutput
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApkDataTest {

    class ApkDataImpl(private val outType: VariantOutput.OutputType) : ApkData() {
        override fun getBaseName(): String = ""

        override fun getFullName(): String = ""

        override fun getType() = outType

        override fun getDirName(): String = ""

        override fun getFilterName(): String? = null
    }

    @Test
    fun testNullLast() {
        val apkData = constructApkData()
        assertThat(apkData).isLessThan(null)
    }

    @Test
    fun testDefaultEqual() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        assertThat(apkData1).isEqualTo(apkData2)
    }

    @Test
    fun testMainTypeFirst() {
        val mainApkData = ApkDataImpl(VariantOutput.OutputType.MAIN)

        VariantOutput.OutputType.values().forEach {
            if (it != VariantOutput.OutputType.MAIN) {
                val apkData = ApkDataImpl(it)
                assertThat(mainApkData).isLessThan(apkData)
            }
        }
    }

    @Test
    fun testVersionCodePrecedence() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.setVersionCode({ 0 })
        apkData2.setVersionCode({ 1 })
        assertThat(apkData1).isLessThan(apkData2)
    }

    @Test
    fun testOutputFileNamePrecedence() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.setOutputFileName("aaa")
        apkData2.setOutputFileName("bbb")
        assertThat(apkData1).isLessThan(apkData2)
    }

    @Test
    fun testOutputFileNamePrecedenceWithNull() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.setOutputFileName("foo")
        assertThat(apkData1).isLessThan(apkData2)
    }

    @Test
    fun testVersionNamePrecedence() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.setVersionName({"aaa"})
        apkData2.setVersionName({"bbb"})
        assertThat(apkData1).isLessThan(apkData2)
    }

    @Test
    fun testVersionNamePrecedenceWithNull() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.setVersionName({"foo"})
        assertThat(apkData1).isLessThan(apkData2)
    }

    @Test
    fun testEnabledPrecedence() {
        val apkData1 = constructApkData()
        val apkData2 = constructApkData()
        apkData1.disable()
        assertThat(apkData1).isLessThan(apkData2)
    }

    private fun constructApkData(): ApkData {
        return ApkDataImpl(VariantOutput.OutputType.MAIN)
    }
}