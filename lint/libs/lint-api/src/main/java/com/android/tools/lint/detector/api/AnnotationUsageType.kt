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

enum class AnnotationUsageType {
    /**
     * An actual annotation on an element. For example, if a detector
     * registers an interest in `androidx.annotation.RequiresApi`, then
     * it will be notified of the element `@RequiresApi(31)` on a method
     * definition.
     *
     * For backwards compatibility, this is not one of the included
     * by default annotation usage types, so you should override
     * [SourceCodeScanner.isApplicableAnnotationUsage], as in
     *
     * ```
     * override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
     *     return type == AnnotationUsageType.DEFINITION || super.isApplicableAnnotationUsage(type)
     * }
     * ```
     */
    DEFINITION,

    /** A call to a method where the method itself was annotated. */
    METHOD_CALL,

    /**
     * A reference to a member in a class where the class was annotated.
     */
    @Deprecated("Use AnnotationInfo.source instead")
    METHOD_CALL_CLASS,

    /**
     * A reference to a member in a package where the package was
     * annotated.
     */
    @Deprecated("Use AnnotationInfo.source instead")
    METHOD_CALL_PACKAGE,

    /**
     * An argument to a method call where the corresponding parameter
     * was annotated.
     */
    METHOD_CALL_PARAMETER,

    /**
     * A method reference (e.g. Class::method) where the corresponding
     * method was annotated.
     */
    METHOD_REFERENCE,

    /**
     * A class reference (such as Foo:class or as another example a
     * cast) where the corresponding class was annotated.
     */
    CLASS_REFERENCE,

    /**
     * An argument to an annotation where the annotation parameter has
     * been annotated.
     */
    ANNOTATION_REFERENCE,

    /** A return from a method that was annotated. */
    METHOD_RETURN,

    /** A method which overrides an annotated method */
    METHOD_OVERRIDE,

    /** A variable whose declaration was annotated. */
    VARIABLE_REFERENCE,

    /**
     * The right hand side of an assignment (or variable/field
     * declaration) where the left hand side was annotated.
     */
    ASSIGNMENT_RHS,

    /**
     * The left hand side of an assignment (or variable/field
     * declaration) where the right hand side is inferred to have been
     * annotated.
     */
    ASSIGNMENT_LHS,

    /**
     * An annotated element is combined with this element in a binary
     * expression (such as +, -, >, ==, != etc.). Note that [EQUALITY]
     * is a special case.
     */
    BINARY,

    /**
     * An annotated element is compared for equality or not equality.
     */
    EQUALITY,

    /** A class extends or implements an annotated element. */
    EXTENDS,

    /** An annotated field is referenced. */
    FIELD_REFERENCE;
}
