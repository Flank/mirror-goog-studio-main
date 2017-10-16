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

import com.android.build.api.dsl.model.FallbackStrategy
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter

class FallbackStrategyImpl(
            private val deprecationReporter: DeprecationReporter,
            issueReporter: EvalIssueReporter)
        : SealableObject(issueReporter), FallbackStrategy {

    private val _matchingFallbacks: SealableList<String> = SealableList.new(issueReporter)

    override var matchingFallbacks: MutableList<String>
        get() = _matchingFallbacks
        set(value) {
            _matchingFallbacks.reset(value)
        }

    internal fun setMatchingFallbacksAsArray(fallbacks: Array<out String>) {
        deprecationReporter.reportDeprecatedUsage(
                "BuildType.matchingFallbacks",
                "BuildType.setMatchingFallbacks",
                DeprecationReporter.DeprecationTarget.VERSION_4_0)
        _matchingFallbacks.reset(fallbacks.toMutableList())
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setMatchingFallbacks(vararg fallbacks: String) {
        setMatchingFallbacksAsArray(fallbacks)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun setMatchingFallbacks(fallback: String) {
        deprecationReporter.reportDeprecatedUsage(
                "BuildType.matchingFallbacks",
                "BuildType.setMatchingFallbacks",
                DeprecationReporter.DeprecationTarget.VERSION_4_0)
        _matchingFallbacks.reset(mutableListOf(fallback))
    }

    internal fun initWith(that: FallbackStrategyImpl) {
        if (checkSeal()) {
            _matchingFallbacks.reset(that._matchingFallbacks)
        }
    }

    override fun seal() {
        super.seal()

        _matchingFallbacks.seal()
    }
}