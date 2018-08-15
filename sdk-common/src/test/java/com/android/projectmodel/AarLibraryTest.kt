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
 * Test cases for [AarLibrary]
 */
class AarLibraryTest {
    @Test
    fun toStringDefaultTest() {
        val cfg = AarLibrary("foo")
        assertThat(cfg.toString()).isEqualTo("AarLibrary(address=foo)")
    }

    @Test
    fun toStringOverrideTest() {
        val cfg = AarLibrary(
            address = "foo",
            classesJar = PathString("/bar/baz")
        )
        assertThat(cfg.toString()).isEqualTo("AarLibrary(address=foo,classesJar=file:///bar/baz)")
    }

    @Test
    fun simpleConstructorTest() {
        assertThat(AarLibrary("foo")).isEqualTo(AarLibrary(address="foo", manifestFile = null))
    }

    @Test
    fun withManifestFileTest() {
        val manifest = PathString("bar")
        val representativeManifest = PathString("representative")
        val address = "foo"
        val lib = AarLibrary(address)
        val withManifest = AarLibrary(address = address, manifestFile = manifest, representativeManifestFile = representativeManifest)

        assertThat(lib.withManifestFile(manifest)).isEqualTo(AarLibrary(address = address, manifestFile = manifest))
        assertThat(lib.withRepresentativeManifestFile(representativeManifest).withManifestFile(manifest))
            .isEqualTo(withManifest)
        assertThat(lib.withManifestFile(manifest).withRepresentativeManifestFile(representativeManifest))
            .isEqualTo(withManifest)
        assertThat(lib.withRepresentativeManifestFile(representativeManifest))
            .isEqualTo(AarLibrary(address = address, representativeManifestFile = representativeManifest))
    }

    @Test
    fun withClassesJarTest() {
        val value = PathString("bar")
        assertThat(AarLibrary("foo").withClassesJar(value))
            .isEqualTo(AarLibrary(address="foo", classesJar = value))
    }

    @Test
    fun withResFolderTest() {
        val value = PathString("bar")
        assertThat(AarLibrary("foo").withResFolder(value))
            .isEqualTo(AarLibrary(address="foo", resFolder = value))
    }

    @Test
    fun withLocationTest() {
        val value = PathString("bar")
        assertThat(AarLibrary("foo").withLocation(value))
            .isEqualTo(AarLibrary(address="foo", location = value))
    }

    @Test
    fun withSymbolFileTest() {
        val value = PathString("bar")
        assertThat(AarLibrary("foo").withSymbolFile(value))
            .isEqualTo(AarLibrary(address="foo", symbolFile = value))
    }
}