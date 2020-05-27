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

package com.android.tools.lint.detector.api

/** Specialized interface for detectors that scan Gradle files  */
interface GradleScanner : FileScanner {

    /**
     * Visits the given DSL construct.
     *
     * The [context] describes the file being analyzed, the [property] describes
     * the property being set, the [value] is the value it is set to, the [parent]
     * is the parent property name, and the (optional) [parentParent] is the parent of
     * the parent property name. The [valueCookie] is a cookie for referencing the
     * value portion of the assignment (which can be passed to {@link GradleContext#getLocation}
     * and the statementCookie is the full range of the whole assignment.
     */
    @Deprecated("Replace with checkDslPropertyAssignment that includes a property cookie")
    fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any
    )

    /**
     * Visits the given DSL construct.
     *
     * The [context] describes the file being analyzed, the [property] describes
     * the property being set, the [value] is the value it is set to, the [parent]
     * is the parent property name, and the (optional) [parentParent] is the parent of
     * the parent property name.
     *
     * The [propertyCookie] is a cookie for referencing the property being assigned,
     * the [valueCookie] is a cookie for referencing the value portion of the assignment
     * (which can be passed to {@link GradleContext#getLocation}
     * and the [statementCookie] is the full range of the whole assignment.
     */
    fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    )

    /**
     * Visits the given DSL reference.
     *
     * The [context] describes the file being analyzed, the [statement] describes
     * the statement being read, the [parent] the parent block name. The [namedArguments]
     * and [unnamedArguments] are the arguments being passed to the block and the [cookie]
     * describes the range and can be passed to {@link GradleContext#getLocation}.
     */
    @Deprecated("Replace with checkMethodCall that includes a parentParent name")
    fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    )

    /**
     * Visits the given DSL reference.
     *
     * The [context] describes the file being analyzed, the [statement] describes
     * the statement being read, the [parent] the parent block name and [parentParent]
     * the grandparent block name. The [namedArguments] and [unnamedArguments] are the
     * arguments being passed to the block and the [cookie] describes the range and
     * can be passed to {@link GradleContext#getLocation}.
     */
    fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    )

    /**
     * Should be true if this scanner will handle visiting the Gradle file
     * on its own. In that case override {@link #visitBuildScript} to process the file.
     */
    val customVisitor: Boolean

    /**
     * Manually visiting the build script. Typically used when you want to look
     * at a Gradle file (e.g. including {@link Scope.GRADLE_FILE}) but not looking
     * at the Gradle file semantically
     */
    fun visitBuildScript(context: Context)
}
