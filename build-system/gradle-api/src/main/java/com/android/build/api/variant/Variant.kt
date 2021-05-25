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
package com.android.build.api.variant

import com.android.build.api.component.Component
import com.android.build.api.component.UnitTest
import org.gradle.api.Incubating
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import java.io.Serializable

/**
 * Parent interface for all types of variants.
 */
interface Variant : Component, HasAndroidResources {

    /**
     * Gets the minimum supported SDK Version for this variant.
     */
    val minSdkVersion: AndroidVersion

    /**
     * Gets the maximum supported SDK Version for this variant.
     */
    val maxSdkVersion: Int?

    /**
     * Gets the target SDK Version for this variant.
     */
    val targetSdkVersion: AndroidVersion

    /**
     * The namespace of the generated R and BuildConfig classes. Also, the namespace used to resolve
     * any relative class names that are declared in the AndroidManifest.xml.
     *
     * This value supersedes any value specified by the `package` attribute in the source
     * AndroidManifest.xml, but doing a 'get' on this property will not retrieve the value specified
     * in the AndroidManifest.xml.
     */
    val namespace: Provider<String>

    /**
     * Variant's [BuildConfigField] which will be generated in the BuildConfig class.
     */
    val buildConfigFields: MapProperty<String, BuildConfigField<out Serializable>>

    /**
     * [MapProperty] of the variant's manifest placeholders.
     *
     * Placeholders are organized with a key and a value. The value is a [String] that will be
     * used as is in the merged manifest.
     *
     * @return the [MapProperty] with keys as [String]
     */
    val manifestPlaceholders: MapProperty<String, String>

    /**
     * Variant's packagingOptions, initialized by the corresponding global DSL element.
     */
    val packaging: Packaging

    /**
     * Variant's cmake [ExternalNativeBuild], initialized by merging the product flavor values or
     * null if no cmake external build is configured for this variant.
     */
    val externalNativeBuild: ExternalNativeBuild?

    /**
     * Variant's [UnitTest], or null if the unit tests for this variant are disabled.
     */
    val unitTest: UnitTest?

    /**
     * Returns an extension object registered via the [VariantBuilder.registerExtension] API or
     * null if none were registered under the passed [type].
     *
     * @return the registered object or null.
     */
    fun <T> getExtension(type: Class<T>): T?

    /**
     * List of proguard configuration files for this variant. The list is initialized from the
     * corresponding DSL element, and cannot be queried at configuration time. At configuration time,
     * you can only add new elements to the list.
     *
     * This list will be initialized from [com.android.build.api.dsl.VariantDimension#proguardFile]
     * for non test related variants and from
     * [com.android.build.api.dsl.VariantDimension.testProguardFiles] for test related variants.
     */
    val proguardFiles: ListProperty<RegularFile>

    /**
     * Additional per variant experimental properties.
     *
     * Initialized from [com.android.build.api.dsl.CommonExtension.experimentalProperties]
     */
    @get:Incubating
    val experimentalProperties: MapProperty<String, Any>
}
