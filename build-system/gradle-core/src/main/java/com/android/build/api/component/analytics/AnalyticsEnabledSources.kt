/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.variant.SourceAndOverlayDirectories
import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledSources @Inject constructor(
    open val delegate: Sources,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
): Sources by delegate {

    override val java: SourceDirectories
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSourceDirectories::class.java,
                delegate.java,
                stats,
                objectFactory)
        }

    override val res: SourceAndOverlayDirectories
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_RES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSourceAndOverlayDirectories::class.java,
                delegate.java,
                stats,
                objectFactory)
        }

    override val assets: SourceAndOverlayDirectories
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_ASSETS_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSourceAndOverlayDirectories::class.java,
                delegate.assets,
                stats,
                objectFactory)
        }

    override val jniLibs: SourceAndOverlayDirectories
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_JNI_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSourceAndOverlayDirectories::class.java,
                delegate.jniLibs,
                stats,
                objectFactory)
        }

    override val shaders: SourceAndOverlayDirectories?
        get() =
            delegate.shaders?.also {
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.SOURCES_SHADERS_ACCESS_VALUE
                objectFactory.newInstance(
                    AnalyticsEnabledSourceAndOverlayDirectories::class.java,
                    it,
                    stats,
                    objectFactory)
            }
    override val mlModels: SourceAndOverlayDirectories
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_ML_MODELS_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledSourceAndOverlayDirectories::class.java,
                delegate.mlModels,
                stats,
                objectFactory)
        }


    override val aidl: SourceDirectories?
        get() = delegate.aidl?.also {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_AIDL_ACCESS_VALUE
            objectFactory.newInstance(
                AnalyticsEnabledSourceDirectories::class.java,
                it,
                stats,
                objectFactory)
        }

    override fun getByName(name: String): SourceDirectories {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE
        return delegate.getByName(name)
    }
}
