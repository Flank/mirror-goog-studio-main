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

package com.android.tools.lint.checks.infrastructure

import junit.framework.TestCase.assertEquals
import org.junit.Test

class ClassNameTest {
    private fun getPackage(source: String): String {
        return ClassName(source).packageName!!
    }

    private fun getClassName(source: String): String {
        return ClassName(source).className!!
    }

    @Test
    fun testGetPackage() {
        assertEquals("foo.bar", getPackage("package foo.bar;"))
        assertEquals("foo.bar", getPackage("package foo.bar"))
    }

    @Test
    fun testGetClass() {
        assertEquals("Foo", getClassName("package foo.bar;\nclass Foo { }"))
        assertEquals("Foo", getClassName("class Foo<T> { }"))
        assertEquals("Foo", getClassName("object Foo : Bar() { }"))
        assertEquals("Foo", getClassName("class Foo(val foo: String) : Bar() { }"))
        assertEquals("ApiCallTest3", getClassName("" +
                "/**\n" +
                " * Call test where the parent class is some other project class which in turn\n" +
                " * extends the public API\n" +
                " */\n" +
                "public class ApiCallTest3 extends Intermediate {}"))
    }
}