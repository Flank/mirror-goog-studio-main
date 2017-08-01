/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.symbols

import com.android.annotations.concurrency.Immutable
import com.android.ide.common.res2.MergingException
import com.android.ide.common.res2.ValueResourceNameValidator
import com.android.resources.ResourceType
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList

/**
 * A symbol is a 4-tuple containing a resource type, a name, a java type and a value. Symbols are
 * used to refer to Android resources.
 *
 * A resource type identifies the group or resource. Resources in Android can have various types:
 * drawables, strings, etc. The full list of supported resource types can be found in
 * `com.android.resources.ResourceType`.
 *
 * The name of the symbol has to be a valid java identifier and is usually the file name of the
 * resource (without the extension) or the name of the resource if the resource is part of an XML
 * file. While names of resources declared in XML files can contain dots and colons, these should be
 * replaced by underscores before being passed to the constructor. To sanitize the resource name,
 * call [SymbolUtils.canonicalizeValueResourceName] before passing the name to the
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
abstract class Symbol private constructor() {

    abstract val resourceType: ResourceType
    abstract val value:String
    abstract val name:String
    abstract val javaType: SymbolJavaType
    /**
     * list of the symbol's children. If the resource has a java type equal to INT_LIST
     * (is a declare styleable) the list will contain its' children in order corresponding to their
     * IDs in the value list. Otherwise, the list is empty.
     * For example:
     * ```
     * int[] styleable S1 {0x7f040001,0x7f040002}
     * int styleable S1_attr1 0
     * int styleable S1_attr2 1
     * ```
     *  corresponds to a Symbol with value `"{0x7f040001,0x7f040002}"` and children `{"attr1",
     * "attr2"}`.
     * */
    abstract val children: ImmutableList<String>

    companion object {

        @JvmField val NO_CHILDREN: ImmutableList<String> = ImmutableList.of()

        /**
         * Creates a new symbol. The `name` of the symbol needs to be a valid sanitized resource
         * name. See [SymbolUtils.canonicalizeValueResourceName] method and apply it beforehand
         * when necessary.
         *
         * @param resourceType the resource type of the symbol
         * @param name the sanitized name of the symbol
         * @param javaType the java type of the symbol
         * @param value the value of the symbol
         */
        @JvmStatic fun createAndValidateSymbol(
                resourceType: ResourceType,
                name: String,
                javaType: SymbolJavaType,
                value: String,
                children: List<String> = ImmutableList.of()): Symbol {
            validateSymbol(name, resourceType)
            return createSymbol(resourceType, name, javaType, value, children)
        }

        /**
         * Creates a new symbol without validation. The `name` of the symbol should to be a valid
         * sanitized resource name.
         *
         * @param resourceType the resource type of the symbol
         * @param name the sanitized name of the symbol
         * @param javaType the java type of the symbol
         * @param value the value of the symbol
         */
        @JvmStatic fun createSymbol(
                resourceType: ResourceType,
                name: String,
                javaType: SymbolJavaType,
                value: String,
                children: List<String> = ImmutableList.of()): Symbol {
            return SymbolImpl(
                    resourceType, value, name, javaType, ImmutableList.copyOf(children))
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

    private data class SymbolImpl(
            override val resourceType: ResourceType,
            override val value: String,
            override val name: String,
            override val javaType: SymbolJavaType,
            override val children: ImmutableList<String>) : Symbol()
}
