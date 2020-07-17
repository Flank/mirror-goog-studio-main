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

package com.android.builder.packaging

import com.android.builder.core.ManifestAttributeSupplier
import com.android.sdklib.AndroidVersion.VersionCodes.O
import com.android.sdklib.AndroidVersion.VersionCodes.P
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.BooleanSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.nio.file.FileSystem
import java.util.function.Predicate
import javax.annotation.CheckReturnValue

class PackagingUtilsTest {

    private val fs: FileSystem by lazy { Jimfs.newFileSystem(Configuration.unix()) }

    @Mock
    lateinit var manifest: ManifestAttributeSupplier

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testGetNoCompressPredicateForExtensions_empty() {
        PackagingUtils.getNoCompressPredicateForExtensions(listOf()).run {
            assertThatTest("foo").isFalse()
            assertThatTest("FOO").isFalse()
            assertThatTest("bar").isFalse()
            assertThatTest("BAR").isFalse()
        }
    }

    @Test
    fun testGetNoCompressPredicateForExtensions_lowerCase() {
        PackagingUtils.getNoCompressPredicateForExtensions(listOf("oo")).run {
            assertThatTest("foo").isTrue()
            assertThatTest("FOO").isTrue()
            assertThatTest("bar").isFalse()
            assertThatTest("BAR").isFalse()
        }
    }

    @Test
    fun testGetNoCompressPredicateForExtensions_upperCase() {
        PackagingUtils.getNoCompressPredicateForExtensions(listOf("OO")).run {
            assertThatTest("foo").isTrue()
            assertThatTest("FOO").isTrue()
            assertThatTest("bar").isFalse()
            assertThatTest("BAR").isFalse()
        }
    }

    @Test
    fun testGetNoCompressPredicateForExtensions_withNonEnglishChars() {
        PackagingUtils
            .getNoCompressPredicateForExtensions(listOf(".koŃcówka"))
            .run {
                // Check if non-English files 'toUpper" and 'toLower' work as expected.
                assertThatTest("file.końcówka").isTrue()
                assertThatTest("file.KOŃCÓWKA").isTrue()
                assertThatTest("file.koŃcówka").isTrue()
                // PL -> EN mapping should not be accepted.
                assertThatTest("file.koncowka").isFalse()
                assertThatTest("file.KONCOWKA").isFalse()
            }
    }

    @Test
    fun testGetNoCompressPredicateForExtensions_withPaths() {
        PackagingUtils
            .getNoCompressPredicateForExtensions(listOf("Android/subFolder/MyFile.idk"))
            .run {
                assertThatTest("Android/subFolder/MyFile.idk").isTrue()
                assertThatTest("android/subfolder/myfile.idk").isTrue()
                assertThatTest("ANDROID/subfolder/MYFILE.idk").isTrue()
                assertThatTest("MainFolder/Android/subFolder/MyFile.idk").isTrue()
                assertThatTest("MyFile.idk").isFalse()
                assertThatTest("subFolder/MyFile.idk").isFalse()
            }
    }

    @Test
    fun testGetNoCompressGlobsForBundle() {
        val matchers = PackagingUtils.getNoCompressGlobsForBundle(listOf("oo"))
        matchers.assertThatTest("foo").isTrue()
        matchers.assertThatTest("FOO").isTrue()
        matchers.assertThatTest("bar").isFalse()
        matchers.assertThatTest("BAR").isFalse()
        // check .tflite (Issue 152875817)
        matchers.assertThatTest("model.tflite").isTrue()
        // check an extension from PackagingUtils.DEFAULT_AAPT_NO_COMPRESS_EXTENSIONS
        matchers.assertThatTest("picture.jpg").isTrue()
        matchers.assertThatTest("native.so").isFalse()
        matchers.assertThatTest("classes.dex").isFalse()
    }

    @Test
    fun testGetNoCompressGlobsForBundle_withPaths() {
        val matchers =
            PackagingUtils.getNoCompressGlobsForBundle(listOf("Android/subFolder/MyFile.idk"))
        matchers.assertThatTest("Android/subFolder/MyFile.idk").isTrue()
        matchers.assertThatTest("android/subfolder/myfile.idk").isTrue()
        matchers.assertThatTest("ANDROID/subfolder/MYFILE.idk").isTrue()
        matchers.assertThatTest("MainFolder/Android/subFolder/MyFile.idk").isTrue()
        matchers.assertThatTest("MyFile.idk").isFalse()
        matchers.assertThatTest("subFolder/MyFile.idk").isFalse()
    }

    @Test
    fun testGetNoCompressGlobsForBundle_withNonEnglishChars() {
        val matchers = PackagingUtils.getNoCompressGlobsForBundle(listOf(".koŃcówka"))
        // Check if non-English files 'toUpper" and 'toLower' work as expected.
        matchers.assertThatTest("file.końcówka").isTrue()
        matchers.assertThatTest("file.KOŃCÓWKA").isTrue()
        matchers.assertThatTest("file.koŃcówka").isTrue()
        // PL -> EN mapping should not be accepted.
        matchers.assertThatTest("file.koncowka").isFalse()
        matchers.assertThatTest("file.KONCOWKA").isFalse()
    }

    @Test
    fun testGetNoCompressGlobsForAapt() {
        // Just check the different format for AAPT, and that it doesn't contain the default no
        // compress extensions (since AAPT already has them included by default).
        val matchers =
            PackagingUtils.getNoCompressForAapt(listOf(".end", ".ąĘ", "Android/foo.bar", ".a(b)c?"))
        val expected = listOf(
            "(a|A)(n|N)(d|D)(r|R)(o|O)(i|I)(d|D)/(f|F)(o|O)(o|O)\\.(b|B)(a|A)(r|R)",
            "\\.(a|A)\\((b|B)\\)(c|C)\\?",
            "\\.(e|E)(n|N)(d|D)",
            "\\.(ą|Ą)(ę|Ę)"
        )
        assertThat(matchers).containsExactlyElementsIn(expected)
    }

    @Test
    fun testGetNoCompressPredicate_doNotCompressNativeLibsOrDex() {
        Mockito.`when`(manifest.extractNativeLibs).thenReturn(false)
        Mockito.`when`(manifest.useEmbeddedDex).thenReturn(true)
        PackagingUtils.getNoCompressPredicate(listOf("oo"), manifest, 1).run {
            // check aaptOptionsNoCompress
            assertThatTest("foo").isTrue()
            // check .tflite (Issue 152875817)
            assertThatTest(".tflite").isTrue()
            // check an extension from PackagingUtils.DEFAULT_AAPT_NO_COMPRESS_EXTENSIONS
            assertThatTest("picture.jpg").isTrue()
            assertThatTest("native.so").isTrue()
            assertThatTest("classes.dex").isTrue()
        }
    }

    @Test
    fun testGetNoCompressPredicate_compressNativeLibsAndDex() {
        Mockito.`when`(manifest.extractNativeLibs).thenReturn(true)
        Mockito.`when`(manifest.useEmbeddedDex).thenReturn(false)
        PackagingUtils.getNoCompressPredicate(listOf("oo"), manifest, 1).run {
            // check aaptOptionsNoCompress
            assertThatTest("foo").isTrue()
            // check .tflite (Issue 152875817)
            assertThatTest(".tflite").isTrue()
            // check an extension from PackagingUtils.DEFAULT_AAPT_NO_COMPRESS_EXTENSIONS
            assertThatTest("picture.jpg").isTrue()
            assertThatTest("native.so").isFalse()
            assertThatTest("classes.dex").isFalse()
        }
    }

    @Test
    fun testGetNoCompressPredicate_compressDexWhenMinSdkO() {
        Mockito.`when`(manifest.extractNativeLibs).thenReturn(null)
        Mockito.`when`(manifest.useEmbeddedDex).thenReturn(null)
        PackagingUtils.getNoCompressPredicate(listOf(), manifest, O).run {
            assertThatTest("classes.dex").isFalse()
        }
    }

    @Test
    fun testGetNoCompressPredicate_doNotCompressDexWhenMinSdkP() {
        Mockito.`when`(manifest.extractNativeLibs).thenReturn(null)
        Mockito.`when`(manifest.useEmbeddedDex).thenReturn(null)
        PackagingUtils.getNoCompressPredicate(listOf(), manifest, P).run {
            assertThatTest("classes.dex").isTrue()
        }
    }

    @Test
    fun testGetNoCompressPredicateForJavaRes() {
        PackagingUtils.getNoCompressPredicateForJavaRes(listOf("oo")).run {
            // check aaptOptionsNoCompress
            assertThatTest("foo").isTrue()
            // check .tflite (Issue 152875817)
            assertThatTest(".tflite").isTrue()
            // check an extension from PackagingUtils.DEFAULT_AAPT_NO_COMPRESS_EXTENSIONS
            assertThatTest("picture.jpg").isTrue()
            assertThatTest("native.so").isFalse()
            assertThatTest("classes.dex").isFalse()
        }
    }


    @CheckReturnValue
    private fun <T> Predicate<T>.assertThatTest(value: T): BooleanSubject {
        return assertThat(this.test(value)).named("Test $value")
    }

    @CheckReturnValue
    private fun Collection<String>.assertThatTest(value: String): BooleanSubject {
        val matchers = this.map { fs.getPathMatcher("glob:$it") }
        return assertThat(matchers.firstOrNull { it.matches(fs.getPath(value)) } != null).named("$value matches")
    }

}