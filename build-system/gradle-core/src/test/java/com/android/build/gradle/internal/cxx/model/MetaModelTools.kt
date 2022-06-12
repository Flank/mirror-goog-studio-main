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

package com.android.build.gradle.internal.cxx.model
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_ABI_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_CMAKE_ABI_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_CMAKE_MODULE_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_MODULE_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_PROJECT_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.CXX_VARIANT_MODEL
import com.android.build.gradle.internal.cxx.model.MetamodelMember.ENVIRONMENT_VARIABLE
import com.android.build.gradle.internal.cxx.settings.EnvironmentVariable
import com.android.build.gradle.internal.cxx.settings.Macro
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Tool used to originally generate cxx/model/MetaModel.kt. That file is now hand maintained but
 * this tool is left here in case we want to use it to make a more sweeping change.
 */
fun main(ignore: Array<String>) {

    val fields = mutableListOf<FieldInfo>()

    // Figure out which members are currently consumed.
    val fieldsUsed = Macro.values().mapNotNull { it.bind?.toString() }.toSet()

    // Gather model data fields
    for(member in MetamodelMember.values()) {
        for(field in member.type.members) {
            if (field !is KProperty<*>) continue
            val macroName = "${member.type.simpleName}_${field.name}".toMacroCase()
            fields.add(
                FieldInfo(
                    modelMember = member,
                    macroName = macroName,
                    getterName = field.name,
                    isExtension = false,
                    isUsed = fieldsUsed.contains(macroName),
                    isNullable = field.returnType.isMarkedNullable,
                    isString = field.returnType.toString().contains("String")
                )
            )
        }
    }

    // Gather extension properties
    for(member in MetamodelMember.values()) {
        val module = member.type.qualifiedName + "Kt"
        val moduleClass : Class<*> = try { Class.forName(module) } catch(e : Throwable) { continue }
        for(method in moduleClass.methods) {
            val trimmed = when {
                method.name.startsWith("get") -> method.name.substring(3).toFirstLower()
                method.name.startsWith("is") -> method.name
                else -> null
            } ?: continue

            val macroName = "${member.type.simpleName!!.toMacroCase()}_${trimmed.toMacroCase()}"
            fields.add(
                FieldInfo(
                    modelMember = member,
                    macroName = macroName,
                    getterName = trimmed,
                    isExtension = true,
                    isUsed = fieldsUsed.contains(macroName),
                    isNullable = false,
                    isString = method.returnType.toString().contains("String")
                )
            )
        }
    }

    val code = StringBuilder()
    code.appendLine("""
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

        package com.android.build.gradle.internal.cxx.model

        enum class ModelField {""".trimIndent())
    for(field in fields.sortedBy { it.macroName }) {
        code.appendLine("    ${field.enumConstruction},")
    }
    code.appendLine("}")

    // Add a method to access properties from a CxxAbiModel
    code.appendLine()
    code.appendLine("""
        /**
         * Lookup up a [ModelField] value from this [CxxAbiModel]. Value is converted to [String].
         */
        fun CxxAbiModel.lookup(field : ModelField) : String? {
            return when(field) {
    """.trimIndent())
    for(field in fields.sortedBy { it.macroName }) {
        if (!field.isUsed) continue

        code.appendLine("        ModelField.${field.macroName} -> ${field.lookupFromAbi}")
    }
    code.appendLine("""
                else -> error(field)
            }
        }
    """.trimIndent())
    println(code)
}

enum class MetamodelMember(val type: KClass<*>) {
    CXX_ABI_MODEL(CxxAbiModel::class),
    CXX_CMAKE_ABI_MODEL(CxxCmakeAbiModel::class),
    CXX_VARIANT_MODEL(CxxVariantModel::class),
    CXX_MODULE_MODEL(CxxModuleModel::class),
    CXX_CMAKE_MODULE_MODEL(CxxCmakeModuleModel::class),
    CXX_PROJECT_MODEL(CxxProjectModel::class),
    // BUILD_SETTINGS_CONFIGURATION(BuildSettingsConfiguration::class),
    ENVIRONMENT_VARIABLE(EnvironmentVariable::class),
}

data class FieldInfo(
    val modelMember : MetamodelMember,
    val macroName : String,
    val getterName : String,
    val isExtension : Boolean,
    val isUsed : Boolean,
    val isNullable : Boolean,
    val isString : Boolean
) {
    val enumConstruction : String = "$macroName"
    private val typeToStringSuffix = when {
        !isString && isNullable -> "?.toString()"
        !isString && !isNullable -> ".toString()"
        else -> ""
    }
    val lookupFromAbi : String = when(modelMember) {
        CXX_ABI_MODEL -> "$getterName$typeToStringSuffix"
        CXX_VARIANT_MODEL -> "variant.$getterName$typeToStringSuffix"
        CXX_MODULE_MODEL -> "variant.module.$getterName$typeToStringSuffix"
        CXX_PROJECT_MODEL -> "variant.module.project.$getterName$typeToStringSuffix"
        CXX_CMAKE_ABI_MODEL -> "cmake?.$getterName$typeToStringSuffix"
        CXX_CMAKE_MODULE_MODEL -> "variant.module.cmake?.$getterName$typeToStringSuffix"
        ENVIRONMENT_VARIABLE -> "error(\"xxx\")"
        else -> error(macroName)
    }
}

private fun String.toMacroCase() : String {
    val result = StringBuilder()
    var inNumber = false
    for(c in this) {
        if (c.isUpperCase()) result.append("_")
        inNumber = if (c.isDigit()) {
            if (!inNumber) result.append("_")
            true
        } else false
        result.append(c.toUpperCase())
    }
    return result.toString().trim('_')
}

private fun String.toFirstLower() : String {
    val result = StringBuilder()
    var isFirst = true
    for(c in this) {
        if (isFirst) {
            result.append(c.toLowerCase())
            isFirst = false
        } else {
            result.append(c)
        }
    }
    return result.toString().trim('_')
}

