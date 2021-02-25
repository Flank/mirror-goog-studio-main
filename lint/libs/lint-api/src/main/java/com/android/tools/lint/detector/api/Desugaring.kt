/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.util.EnumSet

/**
 * Desugaring is an operating to post process the compiled code to
 * transform operations to work on older API levels. For example,
 * lambdas are converted into anonymous inner classes.
 */
enum class Desugaring(val constant: Int) {
    /** Lambdas. */
    LAMBDAS(DESUGARING_LAMBDAS),

    /** Method references. */
    METHOD_REFERENCES(DESUGARING_METHOD_REFERENCES),

    /** Type annotations and repeated annotations. */
    TYPE_ANNOTATIONS(DESUGARING_TYPE_ANNOTATIONS),

    /** Default and static interface methods. */
    INTERFACE_METHODS(DESUGARING_INTERFACE_METHODS),

    /** Try-with-resource statements. */
    TRY_WITH_RESOURCES(DESUGARING_TRY_WITH_RESOURCES),

    /** Inlining Objects.requireNonNull. */
    OBJECTS_REQUIRE_NON_NULL(DESUGARING_OBJECTS_REQUIRE_NON_NULL),

    /** Rewriting Long.compare to lcmp. */
    LONG_COMPARE(DESUGARING_LONG_COMPARE),

    /** Rewriting some Java 8 library calls into backport library. */
    JAVA_8_LIBRARY(DESUGARING_LONG_COMPARE);

    companion object {
        /** No desugaring in effect. */
        @JvmField
        val NONE: Set<Desugaring> = EnumSet.noneOf(Desugaring::class.java)

        /**
         * Default assumed desugaring (used in all recent versions of
         * Gradle, Bazel, etc); does not include [JAVA_8_LIBRARY]
         */
        @JvmField
        val DEFAULT: Set<Desugaring> = EnumSet.of(
            LAMBDAS, METHOD_REFERENCES, TYPE_ANNOTATIONS,
            INTERFACE_METHODS, TRY_WITH_RESOURCES, OBJECTS_REQUIRE_NON_NULL, LONG_COMPARE
        )

        /** Full desugaring. */
        @JvmField
        val FULL: Set<Desugaring> = EnumSet.of(
            LAMBDAS, METHOD_REFERENCES, TYPE_ANNOTATIONS,
            INTERFACE_METHODS, TRY_WITH_RESOURCES, OBJECTS_REQUIRE_NON_NULL, LONG_COMPARE,
            JAVA_8_LIBRARY
        )

        fun fromConstant(constant: Int): Desugaring {
            return when (constant) {
                DESUGARING_LAMBDAS -> LAMBDAS
                DESUGARING_METHOD_REFERENCES -> METHOD_REFERENCES
                DESUGARING_TYPE_ANNOTATIONS -> TYPE_ANNOTATIONS
                DESUGARING_INTERFACE_METHODS -> INTERFACE_METHODS
                DESUGARING_TRY_WITH_RESOURCES -> TRY_WITH_RESOURCES
                DESUGARING_OBJECTS_REQUIRE_NON_NULL -> OBJECTS_REQUIRE_NON_NULL
                DESUGARING_LONG_COMPARE -> LONG_COMPARE
                DESUGARING_JAVA_8_LIBRARY -> JAVA_8_LIBRARY
                else -> error("Unexpected constant $constant")
            }
        }
    }
}

const val DESUGARING_LAMBDAS = 1
const val DESUGARING_METHOD_REFERENCES = 2
const val DESUGARING_TYPE_ANNOTATIONS = 3
const val DESUGARING_INTERFACE_METHODS = 4
const val DESUGARING_TRY_WITH_RESOURCES = 5
const val DESUGARING_OBJECTS_REQUIRE_NON_NULL = 6
const val DESUGARING_LONG_COMPARE = 7
const val DESUGARING_JAVA_8_LIBRARY = 8
