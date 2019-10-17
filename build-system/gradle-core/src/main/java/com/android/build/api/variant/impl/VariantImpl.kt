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

import com.android.build.api.artifact.Operations
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantOutput
import com.android.build.gradle.internal.core.VariantConfiguration
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

internal class VariantImpl(
    override val name: String,
    private val objects: ObjectFactory,
    private val variantScope: VariantScope,
    private val variantConfiguration: VariantConfiguration<*,*,*>,
    override val operations: Operations
    ) : Variant {

    private val variantOutputs= mutableListOf<VariantOutput>()

    fun addVariantOutput(apkData: ApkData): VariantOutputImpl {

        // the DSL objects are now locked, if the versionCode is provided, use that
        // otherwise use the lazy manifest reader to extract the value from the manifest
        // file.
        val versionCode = variantConfiguration.mergedFlavor.versionCode ?: -1
        val versionCodeProperty = initializeProperty(Int::class.java, "$name::versionCode")
        if (versionCode <= 0) {
            versionCodeProperty.set(
                variantScope.globalScope.project.provider<Int> {
                    variantConfiguration.versionCodeSerializableSupplier.asInt
                })
        } else {
            versionCodeProperty.set(versionCode)
        }
        return VariantOutputImpl(versionCodeProperty,
            VariantOutput.OutputType.valueOf(apkData.type.name)).also { variantOutputs.add(it) }
    }

    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    private fun <T> initializeProperty(type: Class<T>, id: String):  Property<T>  {
        return if (variantScope.globalScope.projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(id,
                objects.property(type))
        } else {
            objects.property(type)
        }
    }
}
