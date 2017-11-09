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

import java.util.EnumSet

/** Specialized interface for detectors that scan other files  */
interface OtherFileScanner : FileScanner {
    /**
     * Returns the set of files this scanner wants to consider.  If this includes
     * [Scope.OTHER] then all source files will be checked. Note that the
     * set of files will not just include files of the indicated type, but all files
     * within the relevant source folder. For example, returning [Scope.JAVA_FILE]
     * will not just return `.java` files, but also other resource files such as
     * `.html` and other files found within the Java source folders.
     *
     *
     * Lint will call the [.run]} method when the file should be checked.
     *
     * @return set of scopes that define the types of source files the
     * detector wants to consider
     */
    fun getApplicableFiles(): EnumSet<Scope>
}
