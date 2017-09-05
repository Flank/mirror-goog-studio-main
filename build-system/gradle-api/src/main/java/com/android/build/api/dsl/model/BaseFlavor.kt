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

package com.android.build.api.dsl.model

import com.android.build.api.dsl.options.InstrumentationOptions
import com.android.build.api.dsl.options.PostprocessingFiles
import com.android.build.api.dsl.options.VectorDrawablesOptions
import org.gradle.api.Action

/** Base DSL object used to configure product flavors.  */
interface BaseFlavor : BuildTypeOrProductFlavor, ProductFlavorOrVariant {

    /**
     * Returns whether to enable unbundling mode for embedded wear app.
     *
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     *
     * @return a boolean or null if the value is not set in this flavor
     */
    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     */
    var wearAppUnbundled: Boolean?

    /**
     * Sets a request for a missing flavor dimension in the current module
     *
     * @param dimension the dimension name
     * @param requestedValue the requested value in the dependencies
     */
    fun missingDimensionStrategy(dimension: String, requestedValue: String)

    /**
     * Sets a request for a missing flavor dimension in the current module
     *
     * @param dimension the dimension name
     * @param requestedValues the requested values in the dependencies, in order of priority
     */
    fun missingDimensionStrategy(dimension: String, vararg requestedValues: String)

    /**
     * Sets a request for a missing flavor dimension in the current module
     *
     * @param dimension the dimension name
     * @param requestedValues the requested values in the dependencies, in order of priority
     */
    fun missingDimensionStrategy(dimension: String, requestedValues: List<String>)

    /** Configures the post-processing options with the given action.  */
    fun postprocessing(action: Action<PostprocessingFiles>)

    /** Returns the post-processing option  */
    val postprocessing: PostprocessingFiles

    // --- DEPRECATED

    /**
     * @see .getVectorDrawables
     */
    @Deprecated("Use {@link VectorDrawablesOptions#getGeneratedDensities()}\n      ")
    val generatedDensities: Set<String>?

    /**
     * @see .getVectorDrawables
     * @see .vectorDrawables
     */
    @Deprecated("Use {@link VectorDrawablesOptions#setGeneratedDensities(Iterable)}\n      ")
    fun setGeneratedDensities(densities: Iterable<String>?)

    @Deprecated("Use {@link #setWearAppUnbundled(Boolean)} ")
    fun wearAppUnbundled(wearAppUnbundled: Boolean?)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getApplicationId()}\n      ")
    var testApplicationId: String?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#instrumentationRunnerArgument(String, String)}\n      ")
    fun testInstrumentationRunnerArgument(key: String, value: String)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#addInstrumentationRunnerArguments(Map)}\n      ")
    fun testInstrumentationRunnerArguments(args: Map<String, String>)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setInstrumentationRunner(String)}\n      ")
    var testInstrumentationRunner: String?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setInstrumentationRunnerArguments(Map)}\n      ")
    var testInstrumentationRunnerArguments: Map<String, String>

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setHandleProfiling(boolean)}\n      ")
    fun setTestHandleProfiling(handleProfiling: Boolean)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getHandleProfiling()}\n      ")
    val testHandleProfiling: Boolean?

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#setFunctionalTest(boolean)}\n      ")
    fun setTestFunctionalTest(functionalTest: Boolean)

    /**
     * @see .getInstrumentationOptions
     * @see .instrumentationOptions
     */
    @Deprecated("Use {@link InstrumentationOptions#getFunctionalTest()}\n      ")
    val testFunctionalTest: Boolean?
}
