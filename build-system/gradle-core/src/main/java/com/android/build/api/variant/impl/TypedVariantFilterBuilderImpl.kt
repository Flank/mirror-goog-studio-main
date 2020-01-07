
/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.api.variant.impl
import com.android.build.api.variant.ActionableVariantObject
import com.android.build.api.variant.BuildTypedVariantFilterBuilder
import com.android.build.api.variant.FlavoredVariantFilterBuilder
import com.android.build.api.variant.TypedVariantFilterBuilder
import com.android.build.api.variant.VariantConfiguration
import com.android.build.gradle.internal.api.dsl.DslScope
import org.gradle.api.Action
import java.util.regex.Pattern
import javax.inject.Inject

internal open class TypedVariantFilterBuilderImpl<T, U> @Inject constructor(
    dslScope: DslScope,
    operations: VariantOperations<T>,
    type: Class<U>)
    : TypedVariantFilterBuilder<U> where T: ActionableVariantObject, T: VariantConfiguration, U: T  {

    // due to the type bounds, this does not work as a implementation by delegate...
    @Suppress("UNCHECKED_CAST")
    private val builder = GenericVariantFilterBuilderImpl(dslScope, operations as VariantOperations<U>, type)

    override fun withBuildType(buildType: String): BuildTypedVariantFilterBuilder<U> {
        return builder.withBuildType(buildType)
    }

    override fun withBuildType(buildType: String, action: Action<U>) {
        builder.withBuildType(buildType, action)
    }

    override fun withBuildType(buildType: String, action: U.() -> Unit) {
        builder.withBuildType(buildType, action)
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): FlavoredVariantFilterBuilder<U> {
        return builder.withFlavor(flavorToDimension)
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>, action: Action<U>) {
        builder.withFlavor(flavorToDimension, action)
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>, action: U.() -> Unit) {
        builder.withFlavor(flavorToDimension, action)
    }

    override fun withName(pattern: Pattern, action: Action<U>) {
        builder.withName(pattern, action)
    }

    override fun withName(name: String, action: Action<U>) {
        builder.withName(name, action)
    }

    override fun withName(name: String, action: U.() -> Unit) {
        builder.withName(name, action)
    }
}
