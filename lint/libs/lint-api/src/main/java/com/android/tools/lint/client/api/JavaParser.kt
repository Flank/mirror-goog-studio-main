/*
 * Copyright (C) 2011 The Android Open Source Project
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
@file:Suppress("unused")

package com.android.tools.lint.client.api

/** Temporarily here for compat purposes */
@Deprecated(message = "Deprecated, use package level constants in com.android.tools.lint.client.api")
object JavaParser {
    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_OBJECT"))
    const val TYPE_OBJECT = com.android.tools.lint.client.api.TYPE_OBJECT

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_STRING"))
    const val TYPE_STRING = com.android.tools.lint.client.api.TYPE_STRING

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_INT"))
    const val TYPE_INT = com.android.tools.lint.client.api.TYPE_INT

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_LONG"))
    const val TYPE_LONG = com.android.tools.lint.client.api.TYPE_LONG

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_CHAR"))
    const val TYPE_CHAR = com.android.tools.lint.client.api.TYPE_CHAR

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_FLOAT"))
    const val TYPE_FLOAT = com.android.tools.lint.client.api.TYPE_FLOAT

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_DOUBLE"))
    const val TYPE_DOUBLE = com.android.tools.lint.client.api.TYPE_DOUBLE

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_BOOLEAN"))
    const val TYPE_BOOLEAN = com.android.tools.lint.client.api.TYPE_BOOLEAN

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_SHORT"))
    const val TYPE_SHORT = com.android.tools.lint.client.api.TYPE_SHORT

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_BYTE"))
    const val TYPE_BYTE = com.android.tools.lint.client.api.TYPE_BYTE

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_NULL"))
    const val TYPE_NULL = com.android.tools.lint.client.api.TYPE_NULL

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER"))
    const val TYPE_INTEGER_WRAPPER = com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER"))
    const val TYPE_BOOLEAN_WRAPPER = com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER"))
    const val TYPE_BYTE_WRAPPER = com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER"))
    const val TYPE_SHORT_WRAPPER = com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_LONG_WRAPPER"))
    const val TYPE_LONG_WRAPPER = com.android.tools.lint.client.api.TYPE_LONG_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER"))
    const val TYPE_DOUBLE_WRAPPER = com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER"))
    const val TYPE_FLOAT_WRAPPER = com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER

    @Deprecated(message = "Deprecated", replaceWith = ReplaceWith("com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER"))
    const val TYPE_CHARACTER_WRAPPER = com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
}
