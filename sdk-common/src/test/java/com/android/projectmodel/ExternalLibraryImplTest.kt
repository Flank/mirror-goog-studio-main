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
package com.android.projectmodel

import com.android.ide.common.util.PathString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Test cases for [ExternalLibrary]
 */
class ExternalLibraryImplTest {
    @Test
    fun toStringDefaultTest() {
        val cfg = ExternalLibraryImpl("foo")
        assertThat(cfg.toString()).isEqualTo("ExternalLibraryImpl(address=foo)")
    }

    @Test
    fun toStringOverrideTest() {
        val cfg = ExternalLibraryImpl(
            address = "foo",
            classJars = listOf(PathString("/bar/baz"))
        )
        assertThat(cfg.toString()).isEqualTo("ExternalLibraryImpl(address=foo,classJars=[file:///bar/baz])")
    }

    @Test
    fun simpleConstructorTest() {
        assertThat(ExternalLibraryImpl("foo")).isEqualTo(
            ExternalLibraryImpl(
                address = "foo",
                manifestFile = null
            )
        )
    }

    private val barPath = PathString("bar")

    @Test
    fun withManifestFileTest() {
        val manifest = barPath
        val address = "foo"
        val lib = ExternalLibraryImpl(address)
        val withManifest = ExternalLibraryImpl(
            address = address,
            manifestFile = manifest
        )

        assertThat(lib.withManifestFile(manifest)).isEqualTo(
            ExternalLibraryImpl(
                address = address,
                manifestFile = manifest
            )
        )
        assertThat(
            lib.withManifestFile(
                manifest
            )
        ).isEqualTo(withManifest)
    }

    @Test
    fun withClassesJarTest() {
        assertThat(ExternalLibraryImpl("foo").withClassJars(listOf(barPath)))
            .isEqualTo(ExternalLibraryImpl(address = "foo", classJars = listOf(barPath)))
    }

    @Test
    fun withResFolderTest() {
        assertThat(ExternalLibraryImpl("foo").withResFolder(RecursiveResourceFolder(barPath)))
            .isEqualTo(ExternalLibraryImpl(address = "foo", resFolder = RecursiveResourceFolder(barPath)))
    }

    @Test
    fun withLocationTest() {
        assertThat(ExternalLibraryImpl("foo").withLocation(barPath))
            .isEqualTo(ExternalLibraryImpl(address = "foo", location = barPath))
    }

    @Test
    fun withSymbolFileTest() {
        assertThat(ExternalLibraryImpl("foo").withSymbolFile(barPath))
            .isEqualTo(ExternalLibraryImpl(address = "foo", symbolFile = barPath))
    }

    @Test
    fun testIsEmpty() {
        val testLib = ExternalLibraryImpl("foo")

        // Metadata-only libs are considered to be "empty"
        assertThat(testLib.isEmpty()).isTrue()
        assertThat(testLib.copy(packageName = "bar").isEmpty()).isTrue()

        // Libs with any content are considered non-empty.
        assertThat(testLib.copy(location = PathString("bar")).isEmpty()).isFalse()
        assertThat(testLib.copy(manifestFile = PathString("bar")).isEmpty()).isFalse()
        assertThat(testLib.copy(classJars = listOf(PathString("bar"))).isEmpty()).isFalse()
        assertThat(testLib.copy(dependencyJars = listOf(PathString("bar"))).isEmpty()).isFalse()
        assertThat(testLib.copy(resFolder = RecursiveResourceFolder(PathString("res"))).isEmpty()).isFalse()
        assertThat(testLib.copy(symbolFile = PathString("res")).isEmpty()).isFalse()
        assertThat(testLib.copy(resApkFile = PathString("res")).isEmpty()).isFalse()
    }
}