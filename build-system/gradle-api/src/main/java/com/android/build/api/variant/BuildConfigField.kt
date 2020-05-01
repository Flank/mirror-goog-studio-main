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

package com.android.build.api.variant

import org.gradle.api.Incubating
import java.io.Serializable
import java.lang.Boolean

/**
 * Field definition for the generated BuildConfig class.
 *
 * The field is generated as: <type> <name> = <value>;
 */
@Incubating
data class BuildConfigField<T: Serializable>(
    /**
     * Generated field type, must be one of the [SupportedType]
     */
    val type: SupportedType<T>,

    /**
     * Value of the generated field.
     * If [type] is [String], then [value] should include quotes.
     */
    val value: T,

    /**
     * Optional field comment that will be added to the generated source file or null if no comment
     * is necessary.
     */
    val comment: String?
) : Serializable {

    /**
     * List of supported types for BuildConfig Fields.
     */
    @Incubating
    sealed class SupportedType<T: Serializable>: Serializable {
        @Incubating
        object Boolean: SupportedType<kotlin.Boolean>() {
            fun make(
                value: kotlin.Boolean,
                comment: kotlin.String? = null
            ): BuildConfigField<kotlin.Boolean> =
                BuildConfigField(Boolean, value, comment)

            fun make(value: kotlin.String, comment: kotlin.String? = null) =
                make(value.toBoolean(), comment)
        }
        @Incubating
        object Int: SupportedType<kotlin.Int>() {
            fun make(
                value: kotlin.Int,
                comment: kotlin.String? = null
            ): BuildConfigField<kotlin.Int> =
                BuildConfigField(Int, value, comment)

            fun make(value: kotlin.String, comment: kotlin.String? = null) =
                make(value.toInt(), comment)
        }

        @Incubating
        object Long: SupportedType<kotlin.Long>() {
            fun make(
                value: kotlin.Long,
                comment: kotlin.String? = null
            ): BuildConfigField<kotlin.Long> =
                BuildConfigField(Long, value, comment)

            fun make(value: kotlin.String, comment: kotlin.String? = null): BuildConfigField<kotlin.Long> {
                return if (value.endsWith("L")) {
                    make(value.dropLast(1))
                } else make(value.toLong(), comment)
            }
        }

        @Incubating
        object String: SupportedType<kotlin.String>() {
            fun make(
                value: kotlin.String,
                comment: kotlin.String? = null
            ): BuildConfigField<kotlin.String> =
                BuildConfigField(String, value, comment)
        }
    }

    @Incubating
    companion object {

        /**
         * make a new instance of [BuildConfigField] with a type as a [SupportedType] and value.
         */
        @JvmStatic
        @JvmOverloads
        fun <T : Serializable> make(
            supportedType: SupportedType<T>,
            value: T,
            comment: String? = "Field from Variant API"
        ) =
            BuildConfigField<T>(supportedType, value, comment)
    }
}