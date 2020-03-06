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

import org.gradle.api.Incubating
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Defines a variant's aapt options.
 */
@Incubating
interface AaptOptions {

    /**
     * The list of patterns describing assets to be ignored.
     *
     * See aapt's --ignore-assets flag via `aapt --help`. Note: the --ignore-assets flag accepts a
     * single string of comma-delimited patterns, whereas this property is a list of patterns.
     *
     * This property will be initialized using the corresponding DSL value.
     */
    val ignoreAssetsPatterns: ListProperty<String>

    /**
     * The list of extensions of files that will not be stored compressed in the APK. Setting this
     * to a list containing just an empty string, i.e., `noCompress.set(listOf(""))`,  will
     * trivially disable compression for all files.
     *
     * See aapt's -0 flag via `aapt --help`.
     *
     * This property will be initialized using the corresponding DSL value.
     */
    val noCompress: ListProperty<String>

    /**
     * Whether aapt will return an error if it fails to find an entry for a configuration.
     *
     * See aapt's --error-on-missing-config-entry flag via `aapt --help`
     *
     * This property will be initialized with the corresponding DSL value.
     */
    val failOnMissingConfigEntry: Property<Boolean>

    /**
     * The list of additional parameters to pass to aapt.
     *
     * This property will be initialized using the corresponding DSL value.
     */
    val additionalParameters: ListProperty<String>

    /**
     * Whether the resources in this variant are fully namespaced.
     *
     * This property is incubating and may change in a future release.
     *
     * This property will be initialized with the corresponding DSL value.
     */
    val namespaced: Property<Boolean>
}