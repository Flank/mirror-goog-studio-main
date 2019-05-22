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
import com.android.build.gradle.options.Option.Status.STABLE
import com.android.builder.model.AndroidProject

enum class OptionalBooleanOption(
    override val propertyName: String,
    override val status: Option.Status = Option.Status.EXPERIMENTAL) : Option<Boolean> {
    SIGNING_V1_ENABLED(AndroidProject.PROPERTY_SIGNING_V1_ENABLED, status = STABLE),
    SIGNING_V2_ENABLED(AndroidProject.PROPERTY_SIGNING_V2_ENABLED, status = STABLE),
    IDE_TEST_ONLY(AndroidProject.PROPERTY_TEST_ONLY, status = STABLE),
    ENABLE_R8("android.enableR8", status = Option.Status.Deprecated(DeprecationReporter.DeprecationTarget.ENABLE_R8)),

    /**
     * This project property is read by the firebase plugin, and has no direct impact on AGP behavior.
     *
     * It is included as an OptionalBooleanOption in order that its value, if set, is recorded in the AGP analytics.
     */
    FIREBASE_PERF_PLUGIN_ENABLE_FLAG("firebasePerformanceInstrumentationEnabled", status = STABLE)
    ;

    override fun parse(value: Any): Boolean {
        return parseBoolean(propertyName, value)
    }
}
