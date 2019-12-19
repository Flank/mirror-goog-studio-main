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

package com.android.build.gradle.internal.scope

import com.android.build.VariantOutput
import com.android.build.api.variant.impl.VariantOutputImpl
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

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
        val mainApkData =
            ApkDataImpl(VariantOutput.OutputType.MAIN)

        VariantOutput.OutputType.values().forEach {
            if (it != VariantOutput.OutputType.MAIN) {
                val apkData = ApkDataImpl(it)
                assertThat(mainApkData).isLessThan(apkData)
            }
        }
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
    fun testSerialization() {
        val originalApkData = constructApkData()
        val variantOutputMock: VariantOutputImpl = Mockito.mock(VariantOutputImpl::class.java)
        val versionCodeMock: Property<*> = Mockito.mock(Property::class.java)
        `when`(variantOutputMock.versionCode).thenReturn(versionCodeMock as Property<Int>)
        `when`(versionCodeMock.get()).thenReturn(42)
        val versionNameMock: Property<*> = Mockito.mock(Property::class.java)
        `when`(variantOutputMock.versionName).thenReturn(versionNameMock as Property<String>)
        `when`(versionNameMock.getOrNull()).thenReturn("foo")
        originalApkData.variantOutput = variantOutputMock

        val bytes = serialize(originalApkData)
        val rebuiltApkData = deserialize(bytes)

        assertThat(rebuiltApkData.versionCode).isEqualTo(42)
        assertThat(rebuiltApkData.versionName).isEqualTo("foo")
    }

    private fun constructApkData(): ApkData {
        return ApkDataImpl(VariantOutput.OutputType.MAIN)
    }

    private fun serialize(apkData: ApkData): ByteArray {
        val backingArray = ByteArrayOutputStream()
        val oos = ObjectOutputStream(backingArray)
        oos.writeObject(apkData)
        oos.close()
        return backingArray.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): ApkData {
        val backingArray = ByteArrayInputStream(bytes)
        val ois = ObjectInputStream(backingArray)
        return ois.readObject() as ApkData
    }
}