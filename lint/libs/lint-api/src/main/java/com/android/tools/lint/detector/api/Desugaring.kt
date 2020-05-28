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
enum class Desugaring {
    /** Lambdas */
    LAMBDAS,

    /** Method references */
    METHOD_REFERENCES,

    /** Type annotations and repeated annotations */
    TYPE_ANNOTATIONS,

    /** Default and static interface methods */
    INTERFACE_METHODS,

    /** Try-with-resource statements */
    TRY_WITH_RESOURCES,

    /** Inlining Objects.requireNonNull */
    OBJECTS_REQUIRE_NON_NULL,

    /** Rewriting Long.compare to lcmp */
    LONG_COMPARE,

    /** Rewriting some Java 8 library calls into backport library */
    JAVA_8_LIBRARY;

    companion object {
        /** No desugaring in effect */
        @JvmField
        val NONE: Set<Desugaring> = EnumSet.noneOf(Desugaring::class.java)

        /**
         * Default assumed desugaring (used in all recent versions of Gradle, Bazel, etc);
         * does not include [JAVA_8_LIBRARY]
         */
        @JvmField
        val DEFAULT: Set<Desugaring> = EnumSet.of(
            LAMBDAS, METHOD_REFERENCES, TYPE_ANNOTATIONS,
            INTERFACE_METHODS, TRY_WITH_RESOURCES, OBJECTS_REQUIRE_NON_NULL, LONG_COMPARE
        )

        /**
         * Full desugaring
         */
        @JvmField
        val FULL: Set<Desugaring> = EnumSet.of(
            LAMBDAS, METHOD_REFERENCES, TYPE_ANNOTATIONS,
            INTERFACE_METHODS, TRY_WITH_RESOURCES, OBJECTS_REQUIRE_NON_NULL, LONG_COMPARE,
            JAVA_8_LIBRARY
        )
    }
}
