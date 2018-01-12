/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.EvalIssueReporter

/**
 * This class is responsible for determining which ABIs are needed for the build based on the
 * relevant contents of build.gradle DSL.
 */
class AbiConfigurator(issueReporter: EvalIssueReporter,
        variantName: String,
        ndkHandlerSupportedAbis: Collection<Abi>,
        ndkHandlerDefaultAbis: Collection<Abi>,
        externalNativeBuildAbiFilters: Set<String>,
        ndkConfigAbiFilters: Set<String>,
        splitsFilterAbis: Set<String>,
        ideBuildOnlyTargetAbi: Boolean,
        ideBuildTargetAbi: String?) {

    val allAbis: Collection<String>
    val validAbis: Collection<Abi>

    /** Sort and join a list of strings for an error message */
    private fun sortAndJoin(elements: Collection<String>): String {
        return elements.sorted().joinToString(", ")
    }

    init {
        val ndkHandlerSupportedAbiStrings = ndkHandlerSupportedAbis.map(Abi::getName)
        val userChosenAbis =
                externalNativeBuildAbiFilters union splitsFilterAbis union ndkConfigAbiFilters
        val userMistakes =
                userChosenAbis subtract ndkHandlerSupportedAbiStrings
        if (!userMistakes.isEmpty()) {
            issueReporter.reportError(
                    EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                    "ABIs [${sortAndJoin(userMistakes)}] are not supported for platform. " +
                            "Supported ABIs are [${sortAndJoin(ndkHandlerSupportedAbiStrings)}].",
                    variantName)
        }
        if (userChosenAbis.isEmpty()) {
            // The user didn't explicitly name any ABIs so return the default set
            allAbis = ndkHandlerDefaultAbis.map(Abi::getName)
            validAbis = ndkHandlerDefaultAbis
        } else {
            // The user explicitly named some ABIs
            val recognizeAbleAbiStrings = Abi.values()
                    .map(Abi::getName)
                    .toSet()
            val selectedAbis =
                    sequenceOf(externalNativeBuildAbiFilters,
                            ndkConfigAbiFilters,
                            splitsFilterAbis)
                            .filter { !it.isEmpty() }
                            .fold(recognizeAbleAbiStrings) { total, next -> total intersect next }

            // Produce the list of expected JSON files. This list includes possibly invalid ABIs
            // so that generator can create fallback JSON for them.
            allAbis = selectedAbis union userMistakes
            // These are ABIs that are available on the current platform
            validAbis = selectedAbis.mapNotNull(Abi::getByName)
        }

        // Lastly, if there is an injected ABI set and none of the ABIs is actually buildable by
        // this project then issue an error.
        if (ideBuildOnlyTargetAbi && ideBuildTargetAbi != null && !ideBuildTargetAbi.isEmpty()) {
            val injectedAbis = ideBuildTargetAbi.split(",")
            val injectedLegalAbis = injectedAbis.mapNotNull(Abi::getByName)
            val injectedLegalValidAbis = injectedLegalAbis intersect validAbis
            if (injectedLegalValidAbis.isEmpty()) {
                issueReporter.reportError(
                        EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
                        "ABIs [${ideBuildTargetAbi}] set by " +
                                "'${StringOption.IDE_BUILD_TARGET_ABI.propertyName}' gradle " +
                                "flag is not supported by this project. Supported ABIs " +
                                "are [${sortAndJoin(validAbis.map(Abi::getName))}].",
                        variantName)
            }
        }
    }
}
