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

import com.android.build.api.dsl.model.BuildTypeOrVariant
import com.android.build.api.dsl.options.PostProcessingOptions
import com.android.build.gradle.internal.api.dsl.options.PostProcessingOptionsImpl
import com.android.build.gradle.internal.api.dsl.sealing.OptionalSupplier
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Action

class BuildTypeOrVariantImpl(
            private val typeName: String,
            private val deprecationReporter: DeprecationReporter,
            issueReporter: EvalIssueReporter)
        : SealableObject(issueReporter), BuildTypeOrVariant {

    private val _postProcessing = OptionalSupplier({ PostProcessingOptionsImpl(issueReporter) })

    override var debuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var testCoverageEnabled: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var pseudoLocalesEnabled: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var jniDebuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptDebuggable: Boolean = false
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var renderscriptOptimLevel: Int = 3
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var zipAlignEnabled: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var embedMicroApp: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override var crunchPngs: Boolean = true
        set(value) {
            if (checkSeal()) {
                field = value
            }
        }

    override fun postProcessing(action: Action<PostProcessingOptions>) {
        action.execute(_postProcessing.get(isSealed()))
    }

    override val postProcessing: PostProcessingOptions
        get() = _postProcessing.get(isSealed())

    internal fun initWith(that: BuildTypeOrVariantImpl) {
        if (checkSeal()) {
            _postProcessing.copyFrom(that._postProcessing)

            debuggable = that.debuggable
            testCoverageEnabled = that.testCoverageEnabled
            pseudoLocalesEnabled = that.pseudoLocalesEnabled
            jniDebuggable = that.jniDebuggable
            renderscriptDebuggable = that.renderscriptDebuggable
            renderscriptOptimLevel = that.renderscriptOptimLevel
            zipAlignEnabled = that.zipAlignEnabled
            embedMicroApp = that.embedMicroApp
            crunchPngs = that.crunchPngs
        }
    }

    override fun seal() {
        super.seal()
        _postProcessing.instance?.seal()
    }

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override fun isDebuggable(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.debuggable",
                "$typeName.isDebuggable",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return debuggable
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isTestCoverageEnabled(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.testCoverageEnabled",
                "$typeName.isTestCoverageEnabled",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return testCoverageEnabled
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isEmbedMicroApp(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.testCoverageEnabled",
                "$typeName.isTestCoverageEnabled",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return embedMicroApp
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isPseudoLocalesEnabled(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.pseudoLocalesEnabled",
                "$typeName.isPseudoLocalesEnabled",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return pseudoLocalesEnabled
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isJniDebuggable(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.jniDebuggable",
                "$typeName.isJniDebuggable",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return jniDebuggable
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isRenderscriptDebuggable(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.renderscriptDebuggable",
                "$typeName.isRenderscriptDebuggable",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return renderscriptDebuggable
    }

    @Suppress("OverridingDeprecatedMember")
    override fun isZipAlignEnabled(): Boolean {
        deprecationReporter.reportDeprecatedUsage(
                "$typeName.zipAlignEnabled",
                "$typeName.isZipAlignEnabled",
                DeprecationReporter.DeprecationTarget.OLD_DSL)
        return zipAlignEnabled
    }
}
