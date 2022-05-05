/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.variant

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantServices
import com.android.utils.appendCapitalized
import com.android.utils.capitalizeAndAppend

class TestFixturesVariantData(
    componentIdentity: ComponentIdentity,
    dslInfo: TestFixturesComponentDslInfo,
    variantDependencies: VariantDependencies,
    variantSources: VariantSources,
    paths: VariantPathHelper,
    artifacts: ArtifactsImpl,
    services: VariantServices,
    taskContainer: MutableTaskContainer
): BaseVariantData(
    componentIdentity,
    dslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    services,
    taskContainer
) {

    override val description: String
        get() = if (dslInfo.hasFlavors()) {
            val sb = StringBuilder(50)
            dslInfo.componentIdentity.buildType?.let { sb.appendCapitalized(it) }
            sb.append(" build for flavor ")
            dslInfo.componentIdentity.flavorName?.let { sb.appendCapitalized(it) }
            sb.toString()
        } else {
            dslInfo.componentIdentity.buildType!!.capitalizeAndAppend(" build")
        }
}
