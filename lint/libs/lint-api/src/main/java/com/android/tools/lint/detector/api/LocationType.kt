/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.detector.api

/**
 * Different types of locations that lint can compute for a given
 * element or AST node.
 */
enum class LocationType {
    /**
     * The whole range for the AST element. For an XML attribute for
     * example this includes both the name and attribute parts, and for
     * a Kotlin method, it includes both the signature and the method
     * body.
     */
    ALL,

    /**
     * The default location which applies various heuristics to try to
     * pick a suitable location. For example, for an XML element, this
     * will only include the first line of the element, since we don't
     * want to highlight the entire block which would look noisy (and
     * could hide other issues within the element).
     */
    DEFAULT,

    /**
     * Just the name of the element. For a method, this would be the
     * method name identifier; in an XML element, it would be the tag,
     * and so on.
     */
    NAME,

    /**
     * Just the value part of the element. For an XML attribute, this
     * would be the text inside the quotes, etc.
     */
    VALUE,

    /**
     * In a method call, includes the called method and arguments, but
     * not the receiver. If you want to include the receiver as well,
     * use [ALL].
     */
    CALL_WITH_ARGUMENTS,

    /**
     * In a method call, includes the receiver and the method name, but
     * not the arguments. If you want to include the arguments as well,
     * use [ALL].
     */
    CALL_WITH_RECEIVER
}
