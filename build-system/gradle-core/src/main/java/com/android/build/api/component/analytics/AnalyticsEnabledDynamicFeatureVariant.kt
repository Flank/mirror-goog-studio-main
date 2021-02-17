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

import com.android.build.api.component.AndroidTest
import com.android.build.api.variant.Aapt
import com.android.build.api.variant.ApkPackaging
import com.android.build.api.variant.Dexing
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledDynamicFeatureVariant @Inject constructor(
    override val delegate: DynamicFeatureVariant,
    stats: GradleBuildVariant.Builder,
    objectFactory: ObjectFactory
) : AnalyticsEnabledVariant(delegate, stats, objectFactory), DynamicFeatureVariant {
    override val aapt: Aapt
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.AAPT_OPTIONS_VALUE
            return delegate.aapt
        }

    override fun aaptOptions(action: Aapt.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.AAPT_OPTIONS_ACTION_VALUE
        delegate.aaptOptions(action)
    }

    private val userVisiblePackagingOptions: ApkPackaging by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledApkPackaging::class.java,
            delegate.packaging,
            stats
        )
    }

    override val packaging: ApkPackaging
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.PACKAGING_OPTIONS_VALUE
            return userVisiblePackagingOptions
        }

    override fun packaging(action: ApkPackaging.() -> Unit) {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.PACKAGING_OPTIONS_ACTION_VALUE
        action.invoke(userVisiblePackagingOptions)
    }

    private val userVisibleAndroidTest: AndroidTest? by lazy {
        delegate.androidTest?.let {
            objectFactory.newInstance(
                AnalyticsEnabledAndroidTest::class.java,
                it,
                stats
            )
        }
    }

    override val androidTest: AndroidTest?
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANDROID_TEST_VALUE
            return userVisibleAndroidTest
        }

    private val userVisibleDexing: Dexing by lazy {
        objectFactory.newInstance(
            AnalyticsEnabledDexing::class.java,
            delegate.dexing,
            stats
        )
    }

    override val dexing: Dexing
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.DEXING_VALUE
            return userVisibleDexing
        }
}
