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
        assertEquals(
            "ApiCallTest3",
            getClassName(
                "" +
                    "/**\n" +
                    " * Call test where the parent class is some other project class which in turn\n" +
                    " * extends the public API\n" +
                    " */\n" +
                    "public class ApiCallTest3 extends Intermediate {}"
            )
        )
    }

    @Test
    fun testObjectInPackage() {
        // https://groups.google.com/g/lint-dev/c/MF1KJP4hijo/m/3QkHST3IAAAJ
        assertEquals("com.test.classes.test", getPackage("package com.test.classes.test; class Foo { }"))
        assertEquals("com.test.objects.test", getPackage("package com.test.objects.test; class Foo { }"))
        assertEquals("Foo", getClassName("package com.test.objects.test; class Foo { }"))
    }

    @Test
    fun testAnnotations() {
        assertEquals(
            "Asdf",
            getClassName(
                "" +
                    "package foo;\n" +
                    "@Anno(SomeClass.cass)\n" +
                    "public class Asdf { }"
            )
        )
    }

    @Test
    fun testGetClassName() {
        assertEquals(
            "ClickableViewAccessibilityTest",
            getClassName(
                "" +
                    "package test.pkg;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.view.MotionEvent;\n" +
                    "import android.view.View;\n" +
                    "\n" +
                    "public class ClickableViewAccessibilityTest {\n" +
                    "    // Fails because onTouch does not call view.performClick().\n" +
                    "    private static class InvalidOnTouchListener implements View.OnTouchListener {\n" +
                    "        public boolean onTouch(View v, MotionEvent event) {\n" +
                    "            return false;\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "}\n"
            )
        )
    }

    @Test
    fun testStripComments() {
        assertEquals(
            """
            public class MyClass { String s = "/* This comment is \"in\" a string */" }
            """.trimIndent().trim(),
            stripComments(
                """
                /** Comment */
                // Line comment
                public class MyClass { String s = "/* This comment is \"in\" a string */" }"""
            )
                .trimIndent().trim()
        )
    }
}
