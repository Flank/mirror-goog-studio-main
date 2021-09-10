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

package com.android.build.api.dsl

import org.gradle.api.Action
import java.io.File
import org.gradle.api.Incubating

/**
 * Shared properties between DSL objects that contribute to a library variant.
 *
 * That is, [LibraryBuildType] and [LibraryProductFlavor] and [LibraryDefaultConfig].
 */
interface LibraryVariantDimension : VariantDimension {
    /**
     * Returns whether multi-dex is enabled.
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    @get:Incubating
    @set:Incubating
    var multiDexEnabled: Boolean?

    /**
     * ProGuard rule files to be included in the published AAR.
     *
     * These proguard rule files will then be used by any application project that consumes the
     * AAR (if ProGuard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */

    @get:Incubating
    val consumerProguardFiles: MutableList<File>

    /**
     * Adds a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     *
     * This method has a return value for legacy reasons.
     */

    @Incubating
    fun consumerProguardFile(proguardFile: Any): Any

    /**
     * Adds proguard rule files to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     *
     * This method has a return value for legacy reasons.
     */
    @Incubating
    fun consumerProguardFiles(vararg proguardFiles: Any): Any

    /** The associated signing config or null if none are set on the variant dimension. */
    @get:Incubating
    @set:Incubating
    var signingConfig: ApkSigningConfig?

    /** Options for configuring AAR metadata. */
    @get:Incubating
    val aarMetadata: AarMetadata

    /** Options for configuring AAR metadata. */
    @Incubating
    fun aarMetadata(action: AarMetadata.() -> Unit)

    /** Options for configuring AAR metadata. */
    @Incubating
    fun aarMetadata(action: Action<AarMetadata>)
}
