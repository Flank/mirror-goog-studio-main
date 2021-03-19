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
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertNull
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
    fun testClassLiterals() {
        // Make sure that in Kotlin where there may be no class declaration (e.g. package
        // functions) we don't accidentally match on T::class.java in the source code.
        assertNull(
            "Foo",
            ClassName(
                "" +
                    "package test.pkg\n" +
                    "import android.content.Context\n" +
                    "inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)\n" +
                    "inline fun Context.systemService2() = getSystemService(String::class.java)"
            ).className
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
    fun testImportInPackage() {
        // http://b/119884022 ClassName#CLASS_PATTERN invalid regexp
        assertEquals(
            "foo",
            getPackage(
                """
                package foo;
                import foo.interfaces.ThisIsNotClassName;
                public class NavigationView extends View {
                }
                """.trimIndent()
            )
        )
        assertEquals(
            "NavigationView",
            getClassName(
                """
                package foo;
                import foo.interfaces.ThisIsNotClassName;
                public class NavigationView extends View {
                }
                """.trimIndent()
            )
        )
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

    @Test
    fun testGetEnumClass() {
        @Language("kotlin")
        val source =
            """
            package com.android.tools.lint.detector.api
            enum class Severity { FATAL, ERROR, WARNING, INFORMATIONAL, IGNORE }
            """.trimIndent()
        assertEquals("com.android.tools.lint.detector.api", ClassName(source).packageName)
        assertEquals("Severity", ClassName(source).className)
    }
}
