/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.variant.ExternalNativeCmakeOptions
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty

class ExternalNativeCmakeOptionsImpl(
        mergedExternalNativeCmakeOptions: CoreExternalNativeCmakeOptions,
        variantPropertiesApiServices: VariantPropertiesApiServices
): ExternalNativeCmakeOptions {

    override val abiFilters: SetProperty<String> =
            variantPropertiesApiServices.setPropertyOf(
                    String::class.java,
                    mergedExternalNativeCmakeOptions.abiFilters
            )

    override val arguments: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeCmakeOptions.arguments
            )

    override val cFlags: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeCmakeOptions.getcFlags()
            )

    override val cppFlags: ListProperty<String> =
            variantPropertiesApiServices.listPropertyOf(
                    String::class.java,
                    mergedExternalNativeCmakeOptions.cppFlags
            )

    override val targets: SetProperty<String> =
            variantPropertiesApiServices.setPropertyOf(
                    String::class.java,
                    mergedExternalNativeCmakeOptions.targets
            )
}
