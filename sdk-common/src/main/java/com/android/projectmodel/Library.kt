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
package com.android.projectmodel

/**
 * Represents a dependency for a [Variant].
 */
sealed class Library {
    /**
     * Globally unique identifier for this library, assigned by the build system.
     */
    abstract val address: String
}

/**
 * Represents a dependency on an .AAR file.
 */
data class AarLibrary(
        override val address: String,
        /**
         * Path to the .aar file on the filesystem.
         */
        val location: PathString
) : Library()

/**
 * Represents a dependency on a Java artifact (either a .jar file or a folder containing .class files).
 */
data class JavaLibrary(
        override val address: String,
        /**
         * Path to the file or folder corresponding to this dependency.
         */
        val location: PathString
) : Library()

/**
 * Represents a dependency on another [AndroidProject].
 */
data class ProjectLibrary(
        override val address: String,
        /**
         * Name of the project (matches the project's [AndroidProject.name] attribute).
         */
        val projectName: String,
        /**
         * Variant of the project being depended upon (matches the variant's [Variant.name] attribute).
         */
        val variant: String
) : Library()
