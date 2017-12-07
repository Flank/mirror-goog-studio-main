/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.dsl.model

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.BuildTypeOrProductFlavor
import com.android.build.api.dsl.model.BuildTypeOrVariant
import com.android.build.api.dsl.model.FallbackStrategy
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.core.BuilderConstants
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter

class BuildTypeImpl(
        private val named: String,
        private val variantProperties: VariantPropertiesImpl,
        private val buildTypeOrProductFlavor: BuildTypeOrProductFlavorImpl,
        internal val buildTypeOrVariant: BuildTypeOrVariantImpl,
        private val fallbackStrategy: FallbackStrategyImpl,
        private val deprecationReporter: DeprecationReporter,
        issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter),
        BuildType,
        VariantProperties by variantProperties,
        BuildTypeOrProductFlavor by buildTypeOrProductFlavor,
        BuildTypeOrVariant by buildTypeOrVariant,
        FallbackStrategy by fallbackStrategy {

    override fun getName() = named

    @Suppress("OverridingDeprecatedMember")
    override var crunchPngsDefault: Boolean = name != BuilderConstants.DEBUG
        get() {
            deprecationReporter.reportObsoleteUsage(
                    "BuildType.crunchPngsDefault",
                    DeprecationReporter.DeprecationTarget.EOY2018)
            return field
        }
        set(value) {
            deprecationReporter.reportObsoleteUsage(
                    "BuildType.crunchPngsDefault",
                    DeprecationReporter.DeprecationTarget.EOY2018)
            if (checkSeal()) {
                field = value
            }
        }

    override fun initWith(that: BuildType) {
        val buildType: BuildTypeImpl = that as? BuildTypeImpl ?:
                throw IllegalArgumentException("BuildType not of expected type")

        if (checkSeal()) {
            variantProperties.initWith(buildType.variantProperties)
            buildTypeOrProductFlavor.initWith(buildType.buildTypeOrProductFlavor)
            buildTypeOrVariant.initWith(buildType.buildTypeOrVariant)
            fallbackStrategy.initWith(buildType.fallbackStrategy)
        }
    }

    override fun seal() {
        super.seal()

        variantProperties.seal()
        buildTypeOrProductFlavor.seal()
        buildTypeOrVariant.seal()
        fallbackStrategy.seal()
    }

    // --- DEPRECATED ---

    @Suppress("OverridingDeprecatedMember")
    override fun isCrunchPngs(): Boolean  {
        deprecationReporter.reportDeprecatedUsage(
                "BuildType.crunchPngs",
                "BuildType.isCrunchPngs",
                DeprecationReporter.DeprecationTarget.EOY2018)
        return crunchPngs
    }

    @Suppress("OverridingDeprecatedMember")
    override var minifyEnabled: Boolean
        get() {
            deprecationReporter.reportDeprecatedUsage(
                    "PostProcessingOptions",
                    "BuildType.minifyEnabled",
                    DeprecationReporter.DeprecationTarget.EOY2018)
            return postProcessing.isObfuscate || postProcessing.isRemoveUnusedCode
        }
        set(value) {
            deprecationReporter.reportDeprecatedUsage(
                    "PostProcessingOptions",
                    "BuildType.minifyEnabled",
                    DeprecationReporter.DeprecationTarget.EOY2018)
            postProcessing.isObfuscate = true
            postProcessing.isRemoveUnusedCode = true
        }

    @Suppress("OverridingDeprecatedMember")
    override fun isMinifiedEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override var shrinkResources: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    @Suppress("OverridingDeprecatedMember")
    override fun isShrinkResources(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override val useProguard: Boolean?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    @Suppress("OverridingDeprecatedMember")
    override fun isUseProguard(): Boolean? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isCrunchPngsDefault(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
