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

package com.android.ide.common.symbols

import com.android.annotations.concurrency.Immutable
import com.android.ide.common.resources.MergingException
import com.android.ide.common.resources.ValueResourceNameValidator
import com.android.resources.ResourceVisibility
import com.android.resources.ResourceType
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList

/**
 * Symbols are used to refer to Android resources.
 *
 * A resource type identifies the group or resource. Resources in Android can have various types:
 * drawables, strings, etc. The full list of supported resource types can be found in
 * `com.android.resources.ResourceType`.
 *
 * The name of the symbol has to be a valid java identifier and is usually the file name of the
 * resource (without the extension) or the name of the resource if the resource is part of an XML
 * file. While names of resources declared in XML files can contain dots and colons, these should be
 * replaced by underscores before being passed to the constructor. To sanitize the resource name,
 * call [canonicalizeValueResourceName] before passing the name to the
 * constructor.
 *
 * For example, the resource `drawable/foo.png` has name `foo`.
 * The string `bar` in file `values/strings.xml` with name `bar` has resource name `bar`.
 *
 * The java type is the java data type that contains the resource value.
 * This is generally `int`, but other values (such as `int[]`) are allowed.
 * Type should not contain any whitespaces, be `null` or empty.
 *
 * The value is a java expression that conforms to the resource type and contains the value of
 * the resource. This may be just an integer like `3`, if the resource has type `int`.
 * But may be a more complex expression. For example, if the resource has type `int[]`, the
 * value may be something such as `{1, 2, 3}`.
 *
 * In practice, symbols do not exist by themselves. They are usually part of a symbol table, but
 * this class is independent of any use.
 */
@Immutable
sealed class Symbol {

    abstract val resourceVisibility: ResourceVisibility
    abstract val resourceType: ResourceType
    abstract val name:String
    /** The value as a string. */
    abstract fun getValue():String
    abstract val javaType: SymbolJavaType
    abstract val children: ImmutableList<String>

    companion object {

        @JvmField val NO_CHILDREN: ImmutableList<String> = ImmutableList.of()


        /**
         * Creates a new symbol. The `name` of the symbol needs to be a valid sanitized resource
         * name. See [canonicalizeValueResourceName] method and apply it beforehand
         * when necessary.
         *
         * @param resourceType the resource type of the symbol
         * @param name the sanitized name of the symbol
         * @param idProvider the provider for the value of the symbol
         */
        @JvmStatic fun createAndValidateSymbol(
            resourceType: ResourceType,
            name: String,
            idProvider: IdProvider): NormalSymbol {
            validateSymbol(name, resourceType)
            return NormalSymbol(
                resourceType = resourceType,
                name = name,
                intValue = idProvider.next(resourceType)
            )
        }

        /**
         * Creates a new symbol. The `name` of the symbol needs to be a valid sanitized resource
         * name. See [canonicalizeValueResourceName] method and apply it beforehand
         * when necessary.
         *
         * @param resourceType the resource type of the symbol
         * @param name the sanitized name of the symbol
         * @param value the value of the symbol
         */
        @JvmStatic fun createAndValidateSymbol(
                resourceType: ResourceType,
                name: String,
                value: Int): NormalSymbol {
            validateSymbol(name, resourceType)
            return NormalSymbol(
                resourceType = resourceType,
                name = name,
                intValue = value
            )
        }

        /**
         * Creates a new symbol. The `name` of the symbol needs to be a valid sanitized resource
         * name. See [canonicalizeValueResourceName] method and apply it beforehand
         * when necessary.
         *
         * For example:
         * ```
         * int[] styleable S1 {0x7f040001,0x7f040002}
         * int styleable S1_attr1 0
         * int styleable S1_attr2 1
         * ```
         *  corresponds to a StylableSymbol with value
         *  `[0x7f040001,0x7f040002]` and children `["attr1", "attr2"]`.
         *
         * @param name the sanitized name of the symbol
         */
        @JvmStatic fun createAndValidateStyleableSymbol(
            name: String,
            values: ImmutableList<Int>,
            children: List<String> = ImmutableList.of()): StyleableSymbol {
            validateSymbol(name, ResourceType.STYLEABLE)
            return StyleableSymbol(
                name = name,
                values = values,
                children = ImmutableList.copyOf(children)
            )
        }

        /**
         * Checks whether the given resource name meets all the criteria: cannot be null or empty,
         * cannot contain whitespaces or dots. Also checks if the given resource type is a valid.
         *
         * @param name the name of the resource that needs to be validated
         */
        private fun validateSymbol(name: String?, resourceType: ResourceType) {
            Preconditions.checkArgument(name != null, "Resource name cannot be null")
            Preconditions.checkArgument(
                    !name!!.contains("."), "Resource name cannot contain dots: " + name)
            try {
                ValueResourceNameValidator.validate(name, resourceType, null)
            } catch (e: MergingException) {
                throw IllegalArgumentException(e)
            }

        }
    }

    data class NormalSymbol @JvmOverloads constructor(
        override val resourceType: ResourceType,
        override val name: String,
        val intValue: Int,
        override val resourceVisibility: ResourceVisibility = ResourceVisibility.UNDEFINED
    ) : Symbol() {
        init {
            Preconditions.checkArgument(resourceType != ResourceType.STYLEABLE,
                "Internal Error: Styleables must be represented by StyleableSymbol.")
        }
        override val javaType: SymbolJavaType
            get() = SymbolJavaType.INT
        override fun getValue(): String = "0x${Integer.toHexString(intValue)}"
        override val children: ImmutableList<String>
            get() = throw UnsupportedOperationException("Only styleables have children.")

        override fun toString(): String =
                "$resourceVisibility $resourceType $name = 0x${intValue.toString(16)}"
    }

    data class StyleableSymbol @JvmOverloads constructor(
        override val name: String,
        val values: ImmutableList<Int>,
        /**
         * list of the symbol's children in order corresponding to their IDs in the value list.
         * For example:
         * ```
         * int[] styleable S1 {0x7f040001,0x7f040002}
         * int styleable S1_attr1 0
         * int styleable S1_attr2 1
         * ```
         *  corresponds to a Symbol with value `"{0x7f040001,0x7f040002}"` and children `{"attr1",
         * "attr2"}`.
         * */
        override val children: ImmutableList<String>,
        override val resourceVisibility: ResourceVisibility = ResourceVisibility.UNDEFINED
    ) : Symbol() {
        override val resourceType: ResourceType
            get() = ResourceType.STYLEABLE

        override fun getValue(): String =
            StringBuilder(values.size * 12 + 2).apply {
                    append("{ ")
                    for (i in 0 until values.size) {
                        if (i != 0) { append(", ") }
                        append("0x")
                        append(Integer.toHexString(values[i]))
                    }
                    append(" }")
                }.toString()

        override val javaType: SymbolJavaType
            get() = SymbolJavaType.INT_LIST
    }
}
