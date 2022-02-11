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

import com.android.tools.lint.checks.infrastructure.LoggingTestLintClient
import com.android.tools.lint.checks.infrastructure.TestFiles.hexBytes
import com.android.tools.lint.checks.infrastructure.TestFiles.jar
import com.android.tools.lint.checks.infrastructure.checkTransitiveComparator
import com.android.tools.lint.checks.infrastructure.portablePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

    @Test
    fun testVisitMultiReleaseJar() {
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val root = temporaryFolder.root.canonicalFile

        // Minimal class file: "class x {}", but where we've substituted
        // the major version for a much higher (unsupported) version
        val classFileContents = "" +
            "CAFEBABE00000037000D0A0003000A07000B07000C0100063C696E69743E01\n" +
            "0003282956010004436F646501000F4C696E654E756D6265725461626C6501\n" +
            "000A536F7572636546696C65010006782E6A6176610C000400050100017801\n" +
            "00106A6176612F6C616E672F4F626A65637400200002000300000000000100\n" +
            "0000040005000100060000001D00010001000000052AB70001B10000000100\n" +
            "070000000600010000000100010008000000020009"
        fun classFileWithMajorVersion(version: Int): String {
            assertTrue(version in 16..255) // ensure hex is exactly 2 digits
            return classFileContents
                // "37" in the line above is the major class file format; 0x37 corresponds to
                // decimal 55, which is the class file format for Java 11. Java 17 would be 61.
                // Here we want to test what happens with some future class file format we're
                // not aware of.
                .replace("CAFEBABE00000037", "CAFEBABE000000" + Integer.toHexString(version))
        }

        val jarFile = jar(
            "parent.jar",
            hexBytes(
                // OK
                "x.class",
                // broken file in the main jar content: should complain
                classFileWithMajorVersion(255)
            ),
            // multi-release jar files -- https://openjdk.java.net/jeps/238
            hexBytes(
                "META-INF/versions/16/x.class",
                classFileWithMajorVersion(60) // JDK 16
            ),
            hexBytes(
                "META-INF/versions/17/x.class",
                classFileWithMajorVersion(61) // JDK 17
            ),
            // Some speculative future version we *do* want flagged
            hexBytes(
                "META-INF/versions/128/x.class",
                // broken file in multi-release portion: do NOT complain
                classFileWithMajorVersion(255) // JDK future
            )
        ).createFile(root)

        val client = LoggingTestLintClient()
        val entries = ClassEntry.fromClassPath(client, listOf(jarFile))
        assertEquals(4, entries.size)
        var successfulVisits = 0
        for (entry in entries) {
            val classNode = entry.visit(client) ?: continue
            successfulVisits++
            assertNotNull(classNode)
            assertEquals("x", classNode.name)
            assertEquals("java/lang/Object", classNode.superName)
        }
        assertEquals(
            "Error: Error processing TESTROOT/parent.jar:x.class: broken class file? (Unsupported class file major version 255)",
            client.getLoggedOutput().replace(root.path, "TESTROOT").portablePath()
        )
        assertEquals(2, successfulVisits)

        temporaryFolder.delete()
    }

    private fun getTestClassEntry(p: String) =
        ClassEntry(File(p), File("classes.jar"), File("bin"), ByteArray(0))
}
