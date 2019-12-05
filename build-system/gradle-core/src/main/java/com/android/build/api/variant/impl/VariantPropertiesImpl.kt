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
import com.android.build.api.variant.VariantConfiguration
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantProperties
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.provider.Property

internal open class VariantPropertiesImpl(
    val dslScope: DslScope,
    val variantScope: VariantScope,
    override val operations: Operations,
    configuration: VariantConfiguration
) : VariantProperties, VariantConfiguration by configuration{

    private val variantOutputs= mutableListOf<VariantOutput>()
    private val variantDslInfo = variantScope.variantDslInfo

    override val applicationId: Property<String> = dslScope.objectFactory.property(String::class.java).apply {
        setDisallowChanges(dslScope.providerFactory.provider { variantDslInfo.applicationId })
    }

    fun addVariantOutput(outputType: com.android.build.VariantOutput.OutputType): VariantOutputImpl {
        // the DSL objects are now locked, if the versionCode is provided, use that
        // otherwise use the lazy manifest reader to extract the value from the manifest
        // file.
        val versionCode = variantDslInfo.getVersionCode(true)
        val versionCodeProperty = initializeProperty(Int::class.java, "$name::versionCode")
        if (versionCode <= 0) {
            versionCodeProperty.set(
                variantScope.globalScope.project.provider<Int> {
                    variantDslInfo.manifestVersionCodeSupplier.asInt
                })
        } else {
            versionCodeProperty.set(versionCode)
        }
        // the DSL objects are now locked, if the versionName is provided, use that; otherwise use
        // the lazy manifest reader to extract the value from the manifest file.
        val versionName = variantDslInfo.getVersionName(true)
        val versionNameProperty = initializeProperty(String::class.java, "$name::versionName")
        versionNameProperty.set(
            variantScope.globalScope.project.provider<String> {
                versionName ?: variantDslInfo.manifestVersionNameSupplier.get()
            }
        )
        return VariantOutputImpl(
            versionCodeProperty,
            versionNameProperty,
            VariantOutput.OutputType.valueOf(outputType.name)
        ).also { variantOutputs.add(it) }
    }

    override val outputs: VariantOutputList
        get() = VariantOutputList(variantOutputs.toList())

    private fun <T> initializeProperty(type: Class<T>, id: String):  Property<T>  {
        return if (variantScope.globalScope.projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(id, dslScope.objectFactory.property(type))
        } else {
            dslScope.objectFactory.property(type)
        }
    }
}