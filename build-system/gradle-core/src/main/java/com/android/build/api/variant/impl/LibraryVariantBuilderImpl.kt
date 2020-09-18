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

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.analytics.AnalyticsEnabledLibraryVariantBuilder
import com.android.build.api.component.analytics.AnalyticsEnabledVariantBuilder
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.VariantApiServices
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

open class LibraryVariantBuilderImpl @Inject constructor(
    variantDslInfo: VariantDslInfo,
    variantConfiguration: ComponentIdentity,
    variantApiServices: VariantApiServices
) : VariantBuilderImpl(
    variantDslInfo,
    variantConfiguration,
    variantApiServices
), LibraryVariantBuilder {

    override fun createUserVisibleVariantObject(
        projectServices: ProjectServices,
        stats: GradleBuildVariant.Builder
    ): AnalyticsEnabledVariantBuilder =
        projectServices.objectFactory.newInstance(
            AnalyticsEnabledLibraryVariantBuilder::class.java,
            this,
            stats
        ) as AnalyticsEnabledVariantBuilder

}
