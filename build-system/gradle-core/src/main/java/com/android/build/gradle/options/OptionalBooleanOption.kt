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

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.model.AndroidProject

enum class OptionalBooleanOption(
    override val propertyName: String,
    override val deprecationTarget: DeprecationReporter.DeprecationTarget? = null
) : Option<Boolean> {
    SIGNING_V1_ENABLED(AndroidProject.PROPERTY_SIGNING_V1_ENABLED),
    SIGNING_V2_ENABLED(AndroidProject.PROPERTY_SIGNING_V2_ENABLED),
    IDE_TEST_ONLY(AndroidProject.PROPERTY_TEST_ONLY),
    SERIAL_AAPT2(AndroidProject.PROPERTY_INVOKE_JNI_AAPT2_LINK_SERIALLY),
    ;

    override fun parse(value: Any): Boolean {
        if (value is CharSequence) {
            return java.lang.Boolean.parseBoolean(value.toString())
        }
        if (value is Boolean) {
            return value
        }
        if (value is Number) {
            return value.toInt() != 0
        }
        throw IllegalArgumentException(
            "Cannot parse project property "
                    + this.propertyName
                    + "='"
                    + value
                    + "' of type '"
                    + value.javaClass
                    + "' as boolean."
        )
    }
}
