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
package com.android.tools.lint.checks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotEquals

class DesugaredMethodLookupTest {
    @Test
    fun testFindAll() {
        assertTrue(DesugaredMethodLookup.isDesugared("java/lang/Character", "compare", "(CC)"))

        for (entry in DesugaredMethodLookup.defaultDesugaredMethods) {
            val sharp = entry.indexOf("#")
            assertNotEquals(-1, sharp)
            val paren = entry.indexOf('(', sharp + 1)
            assertNotEquals(-1, paren)

            val owner = entry.substring(0, sharp).replace("/", ".").replace("\$", ".")
            val name = entry.substring(sharp + 1, paren)
            val desc = entry.substring(paren, entry.indexOf(")") + 1)
            assertTrue(entry, DesugaredMethodLookup.isDesugared(owner, name, desc))
        }
    }

    @Test
    fun testNotDesugared() {
        assertFalse(DesugaredMethodLookup.isDesugared("foo.bar.Baz", "foo", "(I)"))
        assertFalse(DesugaredMethodLookup.isDesugared("java/lang/Character", "wrongmethod", "(I)"))
        assertFalse(DesugaredMethodLookup.isDesugared("java/lang/Character", "compare", "(JJJJ)"))
        assertFalse(DesugaredMethodLookup.isDesugared("java/lang/Character", "compare", "()"))
    }

    @Test
    fun testFile() {
        val desc =
            "" +
                "abc/def/GHI\$JKL#abc(III)Z\n" +
                "def/gh/IJ\n" +
                "g/hijk/l/MN#op\n" +
                "hij/kl/mn/O#pQr()Z\n"
        try {
            DesugaredMethodLookup.setDesugaredMethods(desc)
            assertFalse(DesugaredMethodLookup.isDesugared("foo/Bar", "baz", "()"))
            assertTrue(DesugaredMethodLookup.isDesugared("abc/def/GHI\$JKL", "abc", "(III)"))
            assertFalse(DesugaredMethodLookup.isDesugared("abc/def/GHI\$JKL", "ab", "(III)"))
            assertTrue(DesugaredMethodLookup.isDesugared("hij/kl/mn/O", "pQr", "()"))

            // Match methods where the descriptor just lists the class name
            assertTrue(DesugaredMethodLookup.isDesugared("def/gh/IJ", "name", "()"))
            // Match inner classes where the descriptor just lists the top level class name
            assertTrue(DesugaredMethodLookup.isDesugared("def/gh/IJ\$Inner", "name", "()"))
            // Match methods where the descriptor just lists the class and method names
            assertTrue(DesugaredMethodLookup.isDesugared("g/hijk/l/MN", "op", "()"))
            assertFalse(DesugaredMethodLookup.isDesugared("g/hijk/l/MN", "wrongname", "()"))
        } finally {
            DesugaredMethodLookup.reset()
        }
    }
}
