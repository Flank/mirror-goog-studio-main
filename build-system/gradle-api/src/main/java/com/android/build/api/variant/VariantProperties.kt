
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
package com.android.build.api.variant
import com.android.build.api.artifact.Operations
import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Parent interface for all types of variants.
 */
@Incubating
interface VariantProperties: VariantConfiguration, ActionableVariantObject {

    /**
     * Variant's application ID as present in the final manifest file of the APK.
     */
    val applicationId: Property<String>

    /**´
     * Returns the final list of variant outputs.
     * @return read only list of [VariantOutput] for this variant.
     */
    val outputs: List<VariantOutput>
    /**
     * Access to the variant's buildable artifacts for build customization.
     */
    val operations: Operations
}