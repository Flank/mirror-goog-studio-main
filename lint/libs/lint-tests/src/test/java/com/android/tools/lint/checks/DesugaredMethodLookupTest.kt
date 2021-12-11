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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.utils.SdkUtils
import com.android.utils.SdkUtils.fileToUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
        val desc1 =
            "" +
                "abc/def/GHI\$JKL#abc(III)Z\n" +
                "def/gh/IJ\n"
        val desc2 =
            "" +
                "g/hijk/l/MN#op\n" +
                "hij/kl/mn/O#pQr()Z\n"

        fun check() {
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
        }

        // Test single plain file
        val file1 = File.createTempFile("desc1", ".txt")
        val file2 = File.createTempFile("desc2", ".txt")
        file1.writeText(desc1 + desc2)
        try {
            assertNull(DesugaredMethodLookup.setDesugaredMethods(listOf(file1.path)))
            check()
        } finally {
            DesugaredMethodLookup.reset()
        }

        // Test 2 files
        file1.writeText(desc1)
        file2.writeText(desc2)
        try {
            // Reverse order to test our sorting as well
            assertNull(DesugaredMethodLookup.setDesugaredMethods(listOf(file2.path, file1.path)))
            check()
        } finally {
            DesugaredMethodLookup.reset()
        }

        // Test file URL paths
        file1.writeText(desc1 + desc2)
        try {
            assertNull(DesugaredMethodLookup.setDesugaredMethods(listOf(SdkUtils.fileToUrl(file1).toExternalForm())))
            check()
        } finally {
            DesugaredMethodLookup.reset()
        }

        // Test JAR URL
        val source = TestFiles.source("foo/bar/baz.txt", desc1 + desc2)
        val jar = TestFiles.jar("myjar.jar", source)
        val jarFile = jar.createFile(file1.parentFile)
        jarFile.deleteOnExit()

        try {
            val jarPath = "jar:" + fileToUrl(jarFile).toExternalForm() + "!/foo/bar/baz.txt"
            assertNull(DesugaredMethodLookup.setDesugaredMethods(listOf(jarPath)))
            check()
        } finally {
            DesugaredMethodLookup.reset()
        }

        // Test error handling
        file1.delete()
        val missingFile = file1.path
        assertEquals(missingFile, DesugaredMethodLookup.setDesugaredMethods(listOf(missingFile)))
    }
}
