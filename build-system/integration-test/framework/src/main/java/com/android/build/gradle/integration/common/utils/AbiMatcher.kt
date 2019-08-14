/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.internal.core.Abi
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

class AbiMatcher {

    companion object {
        @JvmStatic
        fun sixtyFourBit(): Matcher<List<Abi>> {
            return object : BaseMatcher<List<Abi>>() {
                override fun matches(item: Any): Boolean {
                    return item is List<*>
                            && item.any { it is Abi && it.supports64Bits() }
                }

                override fun describeTo(description: Description) {
                    description
                        .appendText("Has at least one 64 bit ABI")
                }
            }
        }

        @JvmStatic
        fun thirtyTwoBit(): Matcher<List<Abi>> {
            return object : BaseMatcher<List<Abi>>() {
                override fun matches(item: Any): Boolean {
                    return item is List<*>
                            && item.any { it is Abi && !it.supports64Bits() }
                }

                override fun describeTo(description: Description) {
                    description
                        .appendText("Has at least one 32 bit ABI")
                }
            }
        }

        @JvmStatic
        fun anyAbi(): Matcher<List<Abi>> {
            return object : BaseMatcher<List<Abi>>() {
                override fun matches(item: Any): Boolean {
                    return true
                }

                override fun describeTo(description: Description) {
                    description.appendText("All ABIs")
                }
            }
        }
    }
}