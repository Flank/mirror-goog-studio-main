/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * DSL object for configuring APK Splits options. Configuring this object allows you to build <a
 * href="https://developer.android.com/studio/build/configure-apk-splits.html">Multiple APKs</a> and
 * <a href="https://developer.android.com/topic/instant-apps/guides/config-splits.html">
 * Configuration APKs</a>.
 *
 * <p>If your app targets multiple device configurations, such as different screen densities and <a
 * href="https://developer.android.com/ndk/guides/abis.html">Application Binary Interfaces
 * (ABIs)</a>, you might want to avoid packaging resources for all configurations into a single
 * large APK. To reduce download sizes for your users, the Android plugin and Google Play Store
 * provide the following strategies to generate and serve build artifiacts that each target a
 * different device configuration--so users download only the resources they need:
 *
 * <ul>
 *   <li><a href="https://developer.android.com/studio/build/configure-apk-splits.html">Multiple
 *       APKs</a>: use this to generate multiple stand-alone APKs. Each APK contains the code and
 *       resources required for a given device configuration. The Android plugin and Google Play
 *       Store support generating multiple APKs based on screen density and ABI. Because each APK
 *       represents a standalone APK that you upload to the Google Play Store, make sure you
 *       appropriately <a
 *       href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-APK-versions">
 *       assign version codes to each APK</a> so that you are able to manage updates later.
 *   <li><a href="https://developer.android.com/topic/instant-apps/guides/config-splits.html">
 *       Configuration APKs</a>: use this only if you're building <a
 *       href="https://developer.android.com/topic/instant-apps/index.html">Android Instant
 *       Apps</a>. The Android plugin packages your app's device-agnostic code and resources in a
 *       base APK, and each set of device-dependent binaries and resources in separate APKs, called
 *       configuration APKs. Configuration APKs do not represent stand-alone versions of your app.
 *       That is, devices need to download both the base APK and additional configuration APKs from
 *       the Google Play Store to run your instant app. The Android plugin and Google Play Store
 *       support generating configuration APKs based on screen density, ABI, and language locales.
 *       You specify properties in this block just as you would when building multiple APKs.
 *       However, you need to also set <a
 *       href="http://google.github.io/android-gradle-dsl/current/com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:generatePureSplits">
 *       <code>generatePureSplits</code></a> to <code>true</code>.
 * </ul>
 */
open class Splits @Inject constructor(objectFactory: ObjectFactory) {

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-density-split">
     * building per-density APKs</a>.
     */
    val density: DensitySplitOptions =
        objectFactory.newInstance(DensitySplitOptions::class.java)

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
     * building per-ABI APKs</a>.
     */
    val abi: AbiSplitOptions = objectFactory.newInstance(AbiSplitOptions::class.java)

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
     * building per-language (or locale) APKs</a>.
     *
     * <p><b>Note:</b> Building per-language APKs is supported only when <a
     * href="https://developer.android.com/topic/instant-apps/guides/config-splits.html">building
     * configuration APKs</a> for <a
     * href="https://developer.android.com/topic/instant-apps/index.html">Android Instant Apps</a>.
     */
    val language: LanguageSplitOptions =
        objectFactory.newInstance(LanguageSplitOptions::class.java)

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-density-split">
     * building per-density APKs</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * DensitySplitOptions}.
     */
    fun density(action: Action<DensitySplitOptions>) {
        action.execute(density)
    }

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
     * building per-ABI APKs</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * AbiSplitOptions}.
     */
    fun abi(action: Action<AbiSplitOptions>) {
        action.execute(abi)
    }

    /**
     * Encapsulates settings for <a
     * href="https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split">
     * building per-language (or locale) APKs</a>.
     *
     * <p><b>Note:</b> Building per-language APKs is supported only when <a
     * href="https://developer.android.com/topic/instant-apps/guides/config-splits.html">building
     * configuration APKs</a> for <a
     * href="https://developer.android.com/topic/instant-apps/index.html">Android Instant Apps</a>.
     *
     * <p>For more information about the properties you can configure in this block, see {@link
     * LanguageSplitOptions}.
     */
    fun language(action: Action<LanguageSplitOptions>) {
        action.execute(language)
    }

    /**
     * Returns the list of screen density configurations that the plugin will generate separate APKs
     * for.
     *
     * <p>If this property returns <code>null</code>, it means the plugin will not generate separate
     * per-density APKs. That is, each APK will include resources for all screen density
     * configurations your project supports.
     *
     * @return a set of screen density configurations.
     */
    val densityFilters: Set<String>
        get() = density.applicableFilters

    /**
     * Returns the list of ABIs that the plugin will generate separate APKs for.
     *
     * <p>If this property returns <code>null</code>, it means the plugin will not generate separate
     * per-ABI APKs. That is, each APK will include binaries for all ABIs your project supports.
     *
     * @return a set of ABIs.
     */
    val abiFilters: Set<String>
        get() = abi.applicableFilters

    /**
     * Returns the list of languages (or locales) that the plugin will generate separate APKs for.
     *
     * <p>If this property returns <code>null</code>, it means the plugin will not generate separate
     * per-language APKs. That is, each APK will include resources for all languages your project
     * supports.
     *
     * @return a set of languages (or lacales).
     */
    val languageFilters: Set<String>
        get() = language.applicationFilters
}
