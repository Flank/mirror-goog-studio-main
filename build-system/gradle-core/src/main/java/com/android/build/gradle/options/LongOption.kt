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

package com.android.build.gradle.options

enum class LongOption(
    override val propertyName: String,
    override val status: Option.Status
) : Option<Long> {
    // Don't report deprecation twice, hence marked as stable
    DEPRECATED_NDK_COMPILE_LEASE(
        "android.deprecatedNdkCompileLease",
        status = Option.Status.STABLE),
    ;

    override fun parse(value: Any): Long {
        if (value is CharSequence) {
            try {
                return java.lang.Long.parseLong(value.toString())
            } catch (ignored: NumberFormatException) {
                // Throws below.
            }
        }
        if (value is Number) {
            return value.toLong()
        }
        throw IllegalArgumentException(
            "Cannot parse project property "
                    + this.propertyName
                    + "='"
                    + value
                    + "' of type '"
                    + value.javaClass
                    + "' as long."
        )
    }

}
