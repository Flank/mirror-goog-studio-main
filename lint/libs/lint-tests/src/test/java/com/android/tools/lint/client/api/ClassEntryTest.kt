/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.tools.lint.checks.infrastructure.checkTransitiveComparator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [ClassEntry] is mostly indirectly tested by all the other unit tests
 */
class ClassEntryTest {
    @Test
    fun testInnerClassOrdering() {
        // Ensure that we see outer classes before inner classes
        val classes = listOf(
            "Foo.class",
            "Foo\$Bar2.class",
            "Bar.class",
            "Foo\$Bar1\$Inner.class",
            "Foo\$Bar1.class",
            "Bar1.class"
        ).map { getTestClassEntry(it) }
        checkTransitiveComparator(classes)

        assertEquals(
            "" +
                "Bar.class\n" +
                "Bar1.class\n" +
                "Foo.class\n" +
                "Foo\$Bar1.class\n" +
                "Foo\$Bar1\$Inner.class\n" +
                "Foo\$Bar2.class",
            classes.sorted().joinToString("\n") { it.file.name }
        )
    }

    @Test
    fun testCompareTo() {
        // Regression test for 178805864
        val classes = listOf(
            "A.class",
            "A-\$ExternalSyntheticLambda0.class",
            "A\$1.class"
        ).map { getTestClassEntry(it) }.toList()
        checkTransitiveComparator(classes)

        // Unsorted
        assertEquals(
            "" +
                "A.class\n" +
                "A\$1.class\n" +
                "A-\$ExternalSyntheticLambda0.class",
            classes.sorted().joinToString("\n") { it.file.name }
        )
    }

    private fun getTestClassEntry(p: String) =
        ClassEntry(File(p), File("classes.jar"), File("bin"), ByteArray(0))
}
