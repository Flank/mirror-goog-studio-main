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
    /** A call to a method where the method it self was annotated */
    METHOD_CALL,

    /** A reference to a member in a class where the class was annotated */
    METHOD_CALL_CLASS,

    /** A reference to a member in a package where the package was annotated */
    METHOD_CALL_PACKAGE,

    /** An argument to a method call where the corresponding parameter was annotated */
    METHOD_CALL_PARAMETER,

    /** An argument to an annotation where the annotation parameter has been annotated */
    ANNOTATION_REFERENCE,

    /** A return from a method that was annotated */
    METHOD_RETURN,

    /** A variable whose declaration was annotated */
    VARIABLE_REFERENCE,

    /** The right hand side of an assignment (or variable/field declaration) where the
     * left hand side was annotated */
    ASSIGNMENT,

    /**
     * An annotated element is combined with this element in a binary expression
     * (such as +, -, >, ==, != etc.). Note that [EQUALITY] is a special case.
     */
    BINARY,

    /** An annotated element is compared for equality or not equality */
    EQUALITY,

    /** A class extends or implements an annotated element */
    EXTENDS,

    /** An annotated field is referenced */
    FIELD_REFERENCE;
}
