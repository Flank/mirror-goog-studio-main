/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.platformPath
import com.android.tools.lint.client.api.LintClient.Companion.clientName
import com.android.tools.lint.client.api.LintClient.Companion.isGradle
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.client.api.LintClient.Companion.resetClientName
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.cli.common.isWindows
import org.jetbrains.uast.UCallExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.SAXException
import java.io.File
import java.io.FileNotFoundException
import java.util.EnumSet

@Suppress("LintDocExample")
class LintClientTest {
    @Test
    fun testApiLevel() {
        val client = LintCliClient(LintClient.CLIENT_UNIT_TESTS)
        val max = client.highestKnownApiLevel
        assertTrue(max >= 16)
    }

    @Test
    fun testClient() {
        assertTrue(!isGradle || !isStudio)
    }

    @Test
    fun testVersion() {
        val client: LintCliClient = object : LintCliClient(CLIENT_UNIT_TESTS) {
            override fun getSdkHome(): File? {
                return TestUtils.getSdk().toFile()
            }
        }
        val revision = client.getClientRevision()
        assertThat(revision).isNotNull()
        assertThat(revision).isNotEmpty()
        val displayRevision = client.getClientDisplayRevision()
        assertThat(displayRevision).isNotNull()
        assertThat(displayRevision).isNotEmpty()
    }

    @Test
    fun testRelative() {
        val client = LintCliClient(LintClient.CLIENT_UNIT_TESTS)
        fun file(path: String): File {
            return File(path.platformPath())
        }

        assertEquals(file("../../d/e/f").path, client.getRelativePath(file("a/b/c"), file("d/e/f")))
        assertEquals(file("../d/e/f").path, client.getRelativePath(file("a/b/c"), file("a/d/e/f")))
        assertEquals(file("../d/e/f").path, client.getRelativePath(file("1/2/3/a/b/c"), file("1/2/3/a/d/e/f")))
        assertEquals(file("c").path, client.getRelativePath(file("a/b/c"), file("a/b/c")))
        assertEquals(file("../../e").path, client.getRelativePath(file("a/b/c/d/e/f"), file("a/b/c/e")))
        assertEquals(file("d/e/f").path, client.getRelativePath(file("a/b/c/e"), file("a/b/c/d/e/f")))
    }

    @Test
    fun testClientName() {
        resetClientName()
        try {
            clientName
            fail("Expected accessing client name before initialization to fail")
        } catch (t: UninitializedPropertyAccessException) {
            // pass
        }
        clientName = LintClient.CLIENT_UNIT_TESTS
        clientName
    }

    @Test
    fun testGetXmlDocument() {
        lint().sdkHome(TestUtils.getSdk().toFile()).files(
            xml(
                "res/values/test.xml",
                """
                    <resources>
                        <string name="string1">String 1</string>
                    </resources>
                    """
            ).indented(),
            xml(
                "res/values/.ignore.xml",
                """
                    <resources>
                        <string name="ignore">Ignore</string>
                    </resources>
                    """
            ).indented(),
            xml("res/values/empty.xml", ""),
            kotlin(
                """
                fun test() = TODO()
                """
            ).indented()
        )
            .issues(TestXmlParsingDetector.ISSUE)
            .allowAbsolutePathsInMessages(true)
            .testModes(TestMode.DEFAULT)
            .run()
            .expect(
                // We pipe through the IO exception message and that one varies between Mac/Linux and Windows so
                // we have per-platform messages here
                if (isWindows)
                    """
                    res/values/empty.xml: Error: XML file is empty; not a valid document: app\res\values\empty.xml [LintError]
                    res/values/nonexistent.xml: Error: app\res\values\nonexistent.xml (The system cannot find the file specified) [LintError]
                    2 errors, 0 warnings
                    """
                else
                    """
                    res/values/empty.xml: Error: XML file is empty; not a valid document: app/res/values/empty.xml [LintError]
                    res/values/nonexistent.xml: Error: app/res/values/nonexistent.xml (No such file or directory) [LintError]
                    2 errors, 0 warnings
                    """
            )
    }

    /** Detector used by [testGetXmlDocument] */
    @SuppressWarnings("ALL")
    class TestXmlParsingDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {
        override fun getApplicableMethodNames(): List<String> {
            return listOf("TODO")
        }

        override fun visitMethodCall(
            context: JavaContext,
            node: UCallExpression,
            method: PsiMethod
        ) {
            // Test empty XML file
            val values = File(context.file.parentFile.parentFile, "res/values")
            val empty = File(values, "empty.xml")

            // Try to parse an empty XML document directly: expect parseXml to throw a suitable SAXException:
            try {
                context.client.xmlParser.parseXml(empty)
                fail("parseXml did not throw an exception on empty document")
            } catch (e: SAXException) {
                // Calling the pars
                assertTrue(e.message!!.startsWith("XML file is empty"))
            }

            // Using the client method to look up XML documents should instead gracefully return null
            // but report an error (this will show up in the report for this detector)
            assertNull(context.client.getXmlDocument(empty))

            // Next, nonexistent file: should generate IOException (or get reported from getXmlDocument)

            val nonexistent = File(empty.parentFile, "nonexistent.xml")
            try {
                context.client.xmlParser.parseXml(nonexistent)
                fail("parseXml did not throw an exception on nonexistent file")
            } catch (e: FileNotFoundException) {
                // pass
            }

            // Again, expect graceful handling from getXmlDocument (along with reporting an error)
            assertNull(context.client.getXmlDocument(nonexistent))
        }

        companion object {
            @JvmField
            val ISSUE = Issue.create(
                id = "_ResourceRepositoryXmlPArsing",
                briefDescription = "Lint check for testing out XML parsing",
                explanation = "Triggers specific XML parsing and IO errors and makes sure they're gracefully handled",
                category = Category.TESTING, priority = 10, severity = Severity.WARNING,
                implementation = Implementation(
                    TestXmlParsingDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)
                )
            )
        }
    }
}
