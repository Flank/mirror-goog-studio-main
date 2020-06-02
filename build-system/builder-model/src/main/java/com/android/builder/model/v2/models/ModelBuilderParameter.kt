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
package com.android.builder.model.v2.models

/**
 * The parameter for ModelBuilder to build customized models.
 *
 * @since 4.2
 */
interface ModelBuilderParameter {
    /**
     * Whether model builder should build variants or not when building [AndroidProject].
     *
     * @return true if model builder should build variant.
     */
    /**
     * Set whether model builder should build variants or not.
     *
     * @param shouldBuildVariant whether model builder should build variants or not.
     */
    var shouldBuildVariant: Boolean

    /**
     * Whether model builder should schedule source generation post sync or not when building [ ].
     *
     * @return true if model builder should schedule source generation.
     */
    /**
     * Set whether model builder should should schedule source generation post sync or not.
     *
     * @param shouldGenerateSources whether model builder should schedule source generation post
     * sync or not.
     */
    var shouldGenerateSources: Boolean

    /**
     * Returns the name of the variant to build.
     *
     * @return the name of the variant to build.
     */
    fun getVariantName(): String?

    /**
     * Set the name of the variant to build.
     *
     * @param variantName the name of the variant to build.
     */
    fun setVariantName(variantName: String)

    /**
     * If not null then this is the name of the ABI to build.
     *
     * @return the name of the ABI to build.
     */
    fun getAbiName(): String?

    /**
     * Sets the ABI to build.
     *
     * @param abi the ABI to build.
     */
    fun setAbiName(abi: String)
}