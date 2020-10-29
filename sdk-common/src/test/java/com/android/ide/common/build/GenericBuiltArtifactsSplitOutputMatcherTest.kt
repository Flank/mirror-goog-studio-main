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

package com.android.ide.common.build

import com.android.builder.testing.api.DeviceConfigProvider
import com.google.common.collect.Sets
import junit.framework.TestCase
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.util.ArrayList
import java.util.Arrays

/**
 * Tests for [GenericBuiltArtifactsSplitOutputMatcher]
 */
class GenericBuiltArtifactsSplitOutputMatcherTest {

    /** Helper to run InstallHelper.computeMatchingOutput with variable ABI list.  */
    private fun computeBestOutput(
        outputs: List<GenericBuiltArtifact>,
        vararg deviceAbis: String
    ): List<File> {
        val deviceConfigProvider = Mockito.mock(
            DeviceConfigProvider::class.java
        )
        Mockito.`when`(deviceConfigProvider.abis)
            .thenReturn(Arrays.asList(*deviceAbis))
        return GenericBuiltArtifactsSplitOutputMatcher.computeBestOutputs(
            deviceConfigProvider,
            wrap(outputs),
            setOf() /* variantAbiFilters */
        )
    }

    private fun computeBestOutput(
        outputs: List<GenericBuiltArtifact>,
        deviceAbis: Set<String>,
        vararg variantAbiFilters: String
    ): List<File> {
        val deviceConfigProvider = Mockito.mock(
            DeviceConfigProvider::class.java
        )
        Mockito.`when`(deviceConfigProvider.abis)
            .thenReturn(ArrayList(deviceAbis))
        return GenericBuiltArtifactsSplitOutputMatcher.computeBestOutputs(
            deviceConfigProvider,
            wrap(outputs),
            listOf(*variantAbiFilters)
        )
    }

    private fun wrap(builtArtifacts: Collection<GenericBuiltArtifact>): GenericBuiltArtifacts =
        GenericBuiltArtifacts(
            version = 3,
            artifactType = GenericArtifactType("APK", "Directory"),
            applicationId = "com.android.test",
            variantName = "debug",
            elements = builtArtifacts,
            elementType = "File"
        )


    @Test
    fun testSingleOutput() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> =
            ArrayList()
        list.add(getUniversalOutput(1).also { match = it })
        val result = computeBestOutput(list, "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiOnlyWithMatch() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getUniversalOutput(1))
        list.add(getAbiOutput("foo", 2).also { match = it })
        list.add(getAbiOutput("bar", 3))
        val result = computeBestOutput(list, "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiOnlyWithMultiMatch() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1))
        list.add(getAbiOutput("foo", 2))
        list.add(getAbiOutput("bar", 3).also { match = it })
        // bar is preferred over foo
        val result =
            computeBestOutput(list, "bar", "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiPreference() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1))
        list.add(getAbiOutput("foo", 1))
        list.add(getAbiOutput("bar1").also { match = it })
        list.add(getAbiOutput("bar2"))
        // bar is preferred over foo
        val result =
            computeBestOutput(list, "bar", "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiPreferenceForUniveralApk() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        // test where the versionCode match the abi order
        list.add(getUniversalOutput(1).also { match = it })
        list.add(getAbiOutput("foo", 1))
        list.add(getAbiOutput("foo", 1))
        list.add(getAbiOutput("foo", 1))
        // bar is preferred over foo
        val result =
            computeBestOutput(list, "bar", "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiOnlyWithMultiMatch2() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        // test where the versionCode does not match the abi order
        list.add(getUniversalOutput(1))
        list.add(getAbiOutput("foo", 2))
        list.add(getAbiOutput("bar", 3).also { match = it })
        // bar is preferred over foo
        val result =
            computeBestOutput(list, "foo", "bar")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiOnlyWithUniversalMatch() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getUniversalOutput(1).also { match = it })
        list.add(getAbiOutput("foo", 2))
        list.add(getAbiOutput("bar", 3))
        val result = computeBestOutput(list, "zzz")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testAbiOnlyWithNoMatch() {
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getAbiOutput("foo", 1))
        list.add(getAbiOutput("bar", 2))
        val result = computeBestOutput(list, "zzz")
        TestCase.assertEquals(0, result.size)
    }

    @Test
    fun testMultiFilterWithMatch() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getUniversalOutput(1))
        list.add(getOutput("zzz", 2))
        list.add(getOutput("foo", 4).also { match = it })
        list.add(getOutput("foo", 3))
        val result = computeBestOutput(list, "foo")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testMultiFilterWithUniversalMatch() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getUniversalOutput(4).also { match = it })
        list.add(getOutput("zzz", 3))
        list.add(getOutput("bar", 2))
        list.add(getOutput("foo", 1))
        val result = computeBestOutput(list, "zzz")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testMultiFilterWithNoMatch() {
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        var match: GenericBuiltArtifact
        list.add(getOutput("zzz", 1).also { match = it })
        list.add(getOutput("bar", 2))
        list.add(getOutput("foo", 3))
        val result = computeBestOutput(list, "zzz")
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testVariantLevelAbiFilter() {
        var match: GenericBuiltArtifact
        val list: MutableList<GenericBuiltArtifact> =
            ArrayList()
        list.add(getUniversalOutput(1).also { match = it })
        val result = computeBestOutput(
            list,
            Sets.newHashSet("bar", "foo"),
            "foo",
            "zzz"
        )
        TestCase.assertEquals(1, result.size)
        TestCase.assertEquals(File(match.outputFile), result[0])
    }

    @Test
    fun testWrongVariantLevelAbiFilter() {
        val list: MutableList<GenericBuiltArtifact> = ArrayList()
        list.add(getUniversalOutput(1))
        val result = computeBestOutput(
            list,
            Sets.newHashSet("bar", "foo"),
            "zzz"
        )
        TestCase.assertEquals(0, result.size)
    }

    private fun getUniversalOutput(versionCode: Int): GenericBuiltArtifact {
        return GenericBuiltArtifact(
            outputType = "UNIVERSAL",
            outputFile = File("null").absolutePath,
            versionCode = versionCode
        )
    }

    private fun getAbiOutput(
        filter: String,
        versionCode: Int
    ): GenericBuiltArtifact {
        return GenericBuiltArtifact(
            outputType = "ONE_OF_MANY",
            outputFile = File(filter).absolutePath,
            versionCode = versionCode,
            filters = listOf(
                GenericFilterConfiguration("ABI", filter))
        )
    }

    private fun getAbiOutput(
        file: String
    ): GenericBuiltArtifact {
        return GenericBuiltArtifact(
            outputType = "ONE_OF_MANY",
            outputFile = File(file).absolutePath,
            versionCode = 1,
            filters = listOf(
                GenericFilterConfiguration("ABI", "bar"))
        )
    }

    private fun getOutput(
        abiFilter: String,
        versionCode: Int
    ): GenericBuiltArtifact {
        return GenericBuiltArtifact(
            outputType = "ONE_OF_MANY",
            outputFile = File(abiFilter).absolutePath,
            versionCode = versionCode,
            filters = listOf(
                GenericFilterConfiguration("ABI", abiFilter))
        )
    }
}
