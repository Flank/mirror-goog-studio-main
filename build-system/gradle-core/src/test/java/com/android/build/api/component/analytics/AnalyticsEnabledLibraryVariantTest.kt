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

package com.android.build.api.component.analytics

import com.android.build.api.variant.JniLibsPackagingOptions
import com.android.build.api.variant.LibraryPackaging
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.ResourcesPackagingOptions
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class AnalyticsEnabledLibraryVariantTest {
    @Mock
    lateinit var delegate: LibraryVariant

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledLibraryVariant

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = AnalyticsEnabledLibraryVariant(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun getApplicationId() {
        Mockito.`when`(delegate.applicationId).thenReturn(FakeGradleProvider("myApp"))
        Truth.assertThat(proxy.applicationId.get()).isEqualTo("myApp")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.READ_ONLY_APPLICATION_ID_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .applicationId
    }

    @Test
    fun getPackagingOptions() {
        val packagingOptions = Mockito.mock(LibraryPackaging::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsPackagingOptions::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackagingOptions::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packaging).thenReturn(packagingOptions)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.packaging.jniLibs
        proxy.packaging.resources

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(4)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).packaging
    }

    @Test
    fun packagingOptionsActions() {
        val packagingOptions = Mockito.mock(LibraryPackaging::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsPackagingOptions::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackagingOptions::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packaging).thenReturn(packagingOptions)
        val action: LibraryPackaging.() -> Unit = {
            this.jniLibs {}
            this.resources {}
        }
        proxy.packaging(action)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(3)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.map { it.type }
        ).containsExactlyElementsIn(
            listOf(
                VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.JNI_LIBS_PACKAGING_OPTIONS_ACTION_VALUE,
                VariantPropertiesMethodType.RESOURCES_PACKAGING_OPTIONS_ACTION_VALUE
            )
        )
        Mockito.verify(delegate, Mockito.times(1)).packaging
    }
}
