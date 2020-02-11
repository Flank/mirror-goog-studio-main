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

import org.gradle.api.Incubating
import java.io.File

/**
 * Shared properties between DSL objects that contribute to a variant.
 *
 * That is, [BuildType] and [ProductFlavor] and [DefaultConfig].
 */
@Incubating
interface BaseConfig<AnnotationProcessorOptionsT : AnnotationProcessorOptions> {
    /**
     * Application id suffix. It is appended to the "base" application id when calculating the final
     * application id for a variant.
     *
     * In case there are product flavor dimensions specified, the final application id suffix
     * will contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on. All of these will have a dot in
     * between e.g. &quot;defaultSuffix.dimension1Suffix.dimensions2Suffix&quot;.
     */
    var applicationIdSuffix: String?

    /**
     * Version name suffix. It is appended to the "base" version name when calculating the final
     * version name for a variant.
     *
     * In case there are product flavor dimensions specified, the final version name suffix will
     * contain the suffix from the default product flavor, followed by the suffix from product
     * flavor of the first dimension, second dimension and so on.
     */
    var versionNameSuffix: String?

    /**
     * Returns whether multi-dex is enabled.
     *
     * This can be null if the flag is not set, in which case the default value is used.
     */
    var multiDexEnabled: Boolean?

    /**
     * Text file with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * If set, rules from this file are used in combination with the default rules used by the
     * build system.
     */
    var multiDexKeepProguard: File?

    /**
     * Text file that specifies additional classes that will be compiled into the main dex file.
     *
     * Classes specified in the file are appended to the main dex classes computed using
     * `aapt`.
     *
     * If set, the file should contain one class per line, in the following format:
     * `com/example/MyClass.class`
     */
    var multiDexKeepFile: File?

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    val ndk: Ndk

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    fun ndk(action: Ndk.() -> Unit)

    /**
     * Specifies the ProGuard configuration files that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * @return a non-null collection of files.
     * @see .getTestProguardFiles
     */
    var proguardFiles: MutableList<File>

    /**
     * Adds a new ProGuard configuration file.
     *
     * `proguardFile getDefaultProguardFile('proguard-android.txt')`
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * This method has a return value for legacy reasons.
     */
    fun proguardFile(proguardFile: Any): Any

    /**
     * Adds new ProGuard configuration files.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * This method has a return value for legacy reasons.
     */
    fun proguardFiles(vararg files: Any): Any

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
    var consumerProguardFiles: MutableList<File>

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
    fun consumerProguardFiles(vararg proguardFiles: Any): Any

    /**
     * The collection of proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    var testProguardFiles: MutableList<File>

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     *
     * This method has a return value for legacy reasons.
     */
    fun testProguardFile(proguardFile: Any): Any

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     *
     * This method has a return value for legacy reasons.
     */
    fun testProguardFiles(vararg proguardFiles: Any): Any

    /**
     * The manifest placeholders.
     *
     * See
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    var manifestPlaceholders: MutableMap<String, Any>

    /**
     * Adds manifest placeholders.
     *
     * See
     * [Inject Build Variables into the Manifest](https://developer.android.com/studio/build/manifest-build-variables.html).
     */
    fun addManifestPlaceholders(manifestPlaceholders: Map<String, Any>)

    /** Options for configuring Java compilation. */
    val javaCompileOptions: JavaCompileOptions<AnnotationProcessorOptionsT>

    /** Options for configuring Java compilation. */
    fun javaCompileOptions(action: JavaCompileOptions<AnnotationProcessorOptionsT>.() -> Unit)
}
