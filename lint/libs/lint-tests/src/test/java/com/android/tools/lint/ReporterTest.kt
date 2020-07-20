/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.lint

import com.android.tools.lint.Reporter.Companion.encodeUrl
import junit.framework.TestCase

class ReporterTest : TestCase() {
    fun testEncodeUrl() {
        assertEquals("a/b/c", encodeUrl("a/b/c"))
        assertEquals("a/b/c", encodeUrl("a\\b\\c"))
        assertEquals("a/b/c/%24%26%2B%2C%3A%3B%3D%3F%40/foo+bar%25/d", encodeUrl("a/b/c/$&+,:;=?@/foo bar%/d"))
        assertEquals("a/%28b%29/d", encodeUrl("a/(b)/d"))
        assertEquals("a/b+c/d", encodeUrl("a/b c/d")) // + or %20
    }
}
