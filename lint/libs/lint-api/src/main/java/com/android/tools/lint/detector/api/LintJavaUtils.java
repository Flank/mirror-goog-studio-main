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

package com.android.tools.lint.detector.api;

// Class which contains some code which cannot be expressed in Kotlin;
// not public since the public LintUtils methods will more directly expose them
class LintJavaUtils {
    /** Returns true if assertions are enabled */
    @SuppressWarnings("all")
    static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true; // Intentional side-effect
        return assertionsEnabled;
    }
}
