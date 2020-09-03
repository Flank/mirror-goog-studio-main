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
import com.android.build.api.component.AndroidTestProperties
import com.android.build.api.component.UnitTest
import com.android.build.api.component.UnitTestProperties
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.DelayedActionExecutor
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Action

abstract class AnalyticsEnabledVariantBuilder(
        override val delegate: VariantBuilder,
        stats: GradleBuildVariant.Builder
) : AnalyticsEnabledComponent(delegate, stats),
    VariantBuilder {

    private val unitTestActions = DelayedActionExecutor<UnitTest>()
    private val unitTestPropertiesOperations = DelayedActionExecutor<UnitTestProperties>()

    private val androidTestActions = DelayedActionExecutor<AndroidTest>()
    private val androidTestPropertiesOperations = DelayedActionExecutor<AndroidTestProperties>()

    override var minSdkVersion: AndroidVersion
        get() = delegate.minSdkVersion
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type =
                VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE
            delegate.minSdkVersion = value
        }

    override var renderscriptTargetApi: Int
        get() = delegate.renderscriptTargetApi
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder()
                .type = VariantMethodType.RENDERSCRIPT_TARGET_API_VALUE
            delegate.renderscriptTargetApi = value
        }

    override fun unitTest(action: UnitTest.() -> Unit) {
        unitTestActions.registerAction(Action { action(it) })
    }

    fun unitTest(action: Action<UnitTest>) {
        unitTestActions.registerAction(action)
    }

    override fun unitTestProperties(action: UnitTestProperties.() -> Unit) {
        unitTestPropertiesOperations.registerAction(Action { action(it) })
    }

    fun unitTestProperties(action: Action<UnitTestProperties>) {
        unitTestPropertiesOperations.registerAction(action)
    }

    override fun androidTest(action: AndroidTest.() -> Unit) {
        androidTestActions.registerAction(Action { action(it) })
    }

    fun androidTest(action: Action<AndroidTest>) {
        androidTestActions.registerAction(action)
    }

    override fun androidTestProperties(action: AndroidTestProperties.() -> Unit) {
        androidTestPropertiesOperations.registerAction(Action { action(it) })
    }

    fun androidTestProperties(action: Action<AndroidTestProperties>) {
        androidTestPropertiesOperations.registerAction(action)
    }

    // FIXME should be internal
    fun executeUnitTestActions(target: UnitTestImpl) {
        unitTestActions.executeActions(AnalyticsEnabledUnitTest(target, stats))
    }

    // FIXME should be internal
    fun executeUnitTestPropertiesActions(target: UnitTestProperties) {
        unitTestPropertiesOperations.executeActions(target)
    }

    // FIXME should be internal
    fun executeAndroidTestActions(target: AndroidTestImpl) {
        androidTestActions.executeActions(AnalyticsEnabledAndroidTest(target, stats))
    }

    // FIXME should be internal
    fun executeAndroidTestPropertiesActions(target: AndroidTestProperties) {
        androidTestPropertiesOperations.executeActions(target)
    }
}