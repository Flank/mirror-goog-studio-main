/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.ConsumableComponentDslInfo
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.OptimizationDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.ShadersDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import com.android.builder.model.ClassField
import org.gradle.api.file.DirectoryProperty

internal abstract class ConsumableComponentDslInfoImpl internal constructor(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    extension: CommonExtension<*, *, *, *>
) : ComponentDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    services,
    buildDirectory,
    extension
), ConsumableComponentDslInfo {

    // merged flavor delegates

    override val renderscriptTarget: Int
        get() = mergedFlavor.renderscriptTargetApi ?: -1
    override val renderscriptSupportModeEnabled: Boolean
        get() = mergedFlavor.renderscriptSupportModeEnabled ?: false
    override val renderscriptSupportModeBlasEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptSupportModeBlasEnabled
            return value ?: false
        }
    override val renderscriptNdkModeEnabled: Boolean
        get() = mergedFlavor.renderscriptNdkModeEnabled ?: false

    override val manifestPlaceholders: Map<String, String> by lazy {
        val mergedFlavorsPlaceholders: MutableMap<String, String> = mutableMapOf()
        mergedFlavor.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        // so far, blindly override the build type placeholders
        buildTypeObj.manifestPlaceholders.forEach { (key, value) ->
            mergedFlavorsPlaceholders[key] = value.toString()
        }
        mergedFlavorsPlaceholders
    }

    // build type delegates

    override val renderscriptOptimLevel: Int
        get() = buildTypeObj.renderscriptOptimLevel

    // helper methods

    override fun getBuildConfigFields(): Map<String, BuildConfigField<out java.io.Serializable>> {
        val buildConfigFieldsMap =
            mutableMapOf<String, BuildConfigField<out java.io.Serializable>>()

        fun addToListIfNotAlreadyPresent(classField: ClassField, comment: String) {
            if (!buildConfigFieldsMap.containsKey(classField.name)) {
                buildConfigFieldsMap[classField.name] =
                    BuildConfigField(classField.type , classField.value, comment)
            }
        }

        (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from build type: ${buildTypeObj.name}")
        }

        for (flavor in productFlavorList) {
            (flavor as com.android.build.gradle.internal.dsl.ProductFlavor).buildConfigFields.values.forEach { classField ->
                addToListIfNotAlreadyPresent(
                    classField,
                    "Field from product flavor: ${flavor.name}"
                )
            }
        }
        defaultConfig.buildConfigFields.values.forEach { classField ->
            addToListIfNotAlreadyPresent(classField, "Field from default config.")
        }
        return buildConfigFieldsMap
    }

    override val shadersDslInfo: ShadersDslInfo? by lazy(LazyThreadSafetyMode.NONE) {
        ShadersDslInfoImpl(
            defaultConfig, buildTypeObj, productFlavorList
        )
    }

    override val optimizationDslInfo: OptimizationDslInfo by lazy(LazyThreadSafetyMode.NONE) {
        OptimizationDslInfoImpl(
            componentType,
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            services,
            buildDirectory
        )
    }
}
