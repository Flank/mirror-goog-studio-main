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

import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.JniLibsPackagingOptions
import com.android.build.api.variant.PackagingOptions
import com.android.build.api.variant.ResourcesPackagingOptions
import com.android.build.api.variant.Variant
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.MapProperty
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.Serializable

class AnalyticsEnabledVariantPropertiesTest {

    @Mock
    lateinit var delegate: Variant

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledVariant

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        proxy = object: AnalyticsEnabledVariant(delegate, stats, FakeObjectFactory.factory) {}
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
    fun getPackageName() {
        Mockito.`when`(delegate.packageName).thenReturn(FakeGradleProvider("package.name"))
        Truth.assertThat(proxy.packageName.get()).isEqualTo("package.name")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.PACKAGE_NAME_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .packageName
    }

    @Test
    fun getBuildConfigFields() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, BuildConfigField<out Serializable>> =
            Mockito.mock(MapProperty::class.java)
                    as MapProperty<String, BuildConfigField<out Serializable>>
        Mockito.`when`(delegate.buildConfigFields).thenReturn(map)
        Truth.assertThat(proxy.buildConfigFields).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUILD_CONFIG_FIELDS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .buildConfigFields
    }

    @Test
    fun addBuildConfigField() {
        proxy.addBuildConfigField("key", "value", "comment")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ADD_BUILD_CONFIG_FIELD_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .addBuildConfigField("key", "value", "comment")
    }

    @Test
    fun addResValue() {
        proxy.addResValue("name","key", "value", "comment")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ADD_RES_VALUE_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .addResValue("name", "key", "value", "comment")
    }

    @Test
    fun addResValueProvider() {
        val provider = FakeGradleProvider("value")
        proxy.addResValue("name","key", provider, "comment")

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.ADD_RES_VALUE_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .addResValue("name", "key", provider, "comment")
    }

    @Test
    fun getManifestPlaceholders() {
        @Suppress("UNCHECKED_CAST")
        val map: MapProperty<String, String> =
            Mockito.mock(MapProperty::class.java)
                    as MapProperty<String, String>
        Mockito.`when`(delegate.manifestPlaceholders).thenReturn(map)
        Truth.assertThat(proxy.manifestPlaceholders).isEqualTo(map)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.MANIFEST_PLACEHOLDERS_VALUE)
        Mockito.verify(delegate, Mockito.times(1))
            .manifestPlaceholders
    }

    @Test
    fun getPackagingOptions() {
        val packagingOptions = Mockito.mock(PackagingOptions::class.java)
        val jniLibsPackagingOptions = Mockito.mock(JniLibsPackagingOptions::class.java)
        val resourcesPackagingOptions = Mockito.mock(ResourcesPackagingOptions::class.java)
        Mockito.`when`(packagingOptions.jniLibs).thenReturn(jniLibsPackagingOptions)
        Mockito.`when`(packagingOptions.resources).thenReturn(resourcesPackagingOptions)
        Mockito.`when`(delegate.packagingOptions).thenReturn(packagingOptions)
        // simulate a user configuring packaging options for jniLibs and resources
        proxy.packagingOptions.jniLibs
        proxy.packagingOptions.resources

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
        Mockito.verify(delegate, Mockito.times(1)).packagingOptions
    }
}
