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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.ApkProperties
import com.android.build.api.dsl.extension.AppExtension
import com.android.build.api.dsl.extension.BuildProperties
import com.android.build.api.dsl.extension.EmbeddedTestProperties
import com.android.build.api.dsl.extension.OnDeviceTestProperties
import com.android.build.api.dsl.extension.VariantAwareProperties
import com.android.build.api.dsl.variant.ApplicationVariant
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.DomainObjectSet

class AppExtensionImpl(
            private val buildProperties: BuildPropertiesImpl,
            private val variantAwareProperties: VariantAwarePropertiesImpl,
            private val apkPropertiesImpl: ApkPropertiesImpl,
            private val embeddedTestProperties: EmbeddedTestPropertiesImpl,
            private val onDeviceTestProperties: OnDeviceTestPropertiesImpl,
            issueReporter: EvalIssueReporter)
        : SealableObject(issueReporter),
        AppExtension,
        BuildProperties by buildProperties,
        VariantAwareProperties by variantAwareProperties,
        ApkProperties by apkPropertiesImpl,
        EmbeddedTestProperties by embeddedTestProperties,
        OnDeviceTestProperties by onDeviceTestProperties {

    override fun seal() {
        super.seal()

        buildProperties.seal()
        variantAwareProperties.seal()
        apkPropertiesImpl.seal()
        embeddedTestProperties.seal()
        onDeviceTestProperties.seal()
    }

    @Suppress("OverridingDeprecatedMember")
    override val applicationVariants: DomainObjectSet<ApplicationVariant>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    @Suppress("OverridingDeprecatedMember")
    override val buildOutputs: DomainObjectSet<*>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
}