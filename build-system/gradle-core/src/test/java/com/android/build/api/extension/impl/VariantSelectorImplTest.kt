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

package com.android.build.api.extension.impl

import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.VariantBuilder
import com.google.common.truth.Truth
import org.junit.Test
import org.mockito.Mockito
import java.util.regex.Pattern

/**
 * Tests for [VariantSelectorImpl]
 */
internal class VariantSelectorImplTest {

    @Test
    fun testAll() {
        val variantSelector = VariantSelectorImpl().all() as VariantSelectorImpl
        val variant = Mockito.mock(VariantBuilder::class.java)
        Truth.assertThat(variantSelector.appliesTo(variant)).isTrue()
    }

    @Test
    fun testWithName() {
        val variantSelector = VariantSelectorImpl()
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl
        val variantFoo = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantFoo.name).thenReturn("Foo")
        val variantFuo = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantFuo.name).thenReturn("Fuo")
        val variantBar = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantBar.name).thenReturn("Bar")
        Truth.assertThat(variantSelector.appliesTo(variantFoo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantFuo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantBar)).isFalse()
    }

    @Test
    fun testWithBuildType() {
        val variantSelector = VariantSelectorImpl()
                .withBuildType("debug") as VariantSelectorImpl
        val debugVariant1 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(debugVariant1.buildType).thenReturn("debug")
        val debugVariant2 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(debugVariant2.buildType).thenReturn("debug")
        val releaseVariant = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(releaseVariant.buildType).thenReturn("release")
        Truth.assertThat(variantSelector.appliesTo(debugVariant1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(debugVariant2)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(releaseVariant)).isFalse()
    }

    @Test
    fun testWithBuildTypeAndName() {
        val variantSelector = VariantSelectorImpl()
                .withBuildType("debug")
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl
        val debugVariant1 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(debugVariant1.buildType).thenReturn("debug")
        Mockito.`when`(debugVariant1.name).thenReturn("Foo")
        val debugVariant2 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(debugVariant2.buildType).thenReturn("debug")
        Mockito.`when`(debugVariant2.name).thenReturn("Bar")
        val releaseVariant = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(releaseVariant.buildType).thenReturn("release")
        Mockito.`when`(releaseVariant.name).thenReturn("Foo")
        Truth.assertThat(variantSelector.appliesTo(debugVariant1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(debugVariant2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(releaseVariant)).isFalse()
    }

    @Test
    fun testWithProductFlavor() {
        val variantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1") as VariantSelectorImpl
        val flavor1Variant = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(flavor1Variant.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        val flavor2variant = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(flavor2variant.productFlavors).thenReturn(
                listOf("dim2" to "flavor2", "dim3" to "flavor3"))
        Truth.assertThat(variantSelector.appliesTo(flavor1Variant)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(flavor2variant)).isFalse()
    }

    @Test
    fun testWithBuildTypeAndFlavor() {
        val variantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1")
                .withBuildType("debug") as VariantSelectorImpl

        val applicationVariantBuilder1 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(applicationVariantBuilder1.buildType).thenReturn("debug")
        Mockito.`when`(applicationVariantBuilder1.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        val applicationVariantBuilder2 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(applicationVariantBuilder2.buildType).thenReturn("release")
        Mockito.`when`(applicationVariantBuilder2.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        val libraryVariantBuilder = Mockito.mock(LibraryVariantBuilder::class.java)
        Mockito.`when`(libraryVariantBuilder.buildType).thenReturn("debug")
        Mockito.`when`(libraryVariantBuilder.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))

        Truth.assertThat(variantSelector.appliesTo(applicationVariantBuilder1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(applicationVariantBuilder2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(libraryVariantBuilder)).isTrue()
    }

    @Test
    fun testWithBuildTypeAndFlavorAndName() {
        val variantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1")
                .withBuildType("debug")
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl

        val variantFoo = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantFoo.buildType).thenReturn("debug")
        Mockito.`when`(variantFoo.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        Mockito.`when`(variantFoo.name).thenReturn("Foo")

        val variantBar = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantBar.buildType).thenReturn("debug")
        Mockito.`when`(variantBar.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        Mockito.`when`(variantBar.name).thenReturn("Bar")

        val variantFoo2 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantFoo2.buildType).thenReturn("release")
        Mockito.`when`(variantFoo2.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        Mockito.`when`(variantFoo2.name).thenReturn("Foo")

        val variantFoo3 = Mockito.mock(ApplicationVariantBuilder::class.java)
        Mockito.`when`(variantFoo3.buildType).thenReturn("release")
        Mockito.`when`(variantFoo3.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        Mockito.`when`(variantFoo3.name).thenReturn("Foo")

        val libraryVariantBuilder = Mockito.mock(LibraryVariantBuilder::class.java)
        Mockito.`when`(libraryVariantBuilder.buildType).thenReturn("debug")
        Mockito.`when`(libraryVariantBuilder.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        Mockito.`when`(libraryVariantBuilder.name).thenReturn("Foo")

        Truth.assertThat(variantSelector.appliesTo(variantFoo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantBar)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(variantFoo2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(variantFoo3)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(libraryVariantBuilder)).isTrue()
    }
}
