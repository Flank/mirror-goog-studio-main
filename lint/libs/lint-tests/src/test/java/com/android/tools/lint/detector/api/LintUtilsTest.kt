/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.builder.model.ApiVersion
import com.android.ide.common.repository.GradleVersion
import com.android.resources.ResourceFolderType
import com.android.sdklib.IAndroidTarget
import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.findKotlinStdlibPath
import com.android.tools.lint.client.api.LintClient.Companion.CLIENT_UNIT_TESTS
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.client.api.TYPE_BOOLEAN
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHAR
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_SHORT
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.tools.lint.client.api.XmlParser
import com.android.utils.Pair
import com.android.utils.SdkUtils.escapePropertyValue
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import com.google.common.collect.Iterables
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.pom.java.LanguageLevel
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.Arrays
import java.util.EnumSet
import java.util.Locale

class LintUtilsTest : TestCase() {
    fun testPrintList() {
        assertEquals("bar, baz, foo", formatList(Arrays.asList("foo", "bar", "baz"), 3))
        assertEquals(
            "foo, bar, baz",
            formatList(Arrays.asList("foo", "bar", "baz"), 3, false)
        )
        assertEquals(
            "foo, bar, baz",
            formatList(Arrays.asList("foo", "bar", "baz"), 5, false)
        )

        assertEquals(
            "foo, bar, baz... (3 more)",
            formatList(Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 3, false)
        )
        assertEquals(
            "foo... (5 more)",
            formatList(Arrays.asList("foo", "bar", "baz", "4", "5", "6"), 1, false)
        )
        assertEquals(
            "foo, bar, baz",
            formatList(Arrays.asList("foo", "bar", "baz"), 0, false)
        )

        assertEquals(
            "foo, bar and baz",
            formatList(Arrays.asList("foo", "bar", "baz"), 0, false, true)
        )
    }

    fun testIsDataBindingExpression() {
        assertTrue(isDataBindingExpression("@{foo}"))
        assertTrue(isDataBindingExpression("@={foo}"))
        assertFalse(isDataBindingExpression("@string/foo"))
        assertFalse(isDataBindingExpression("?string/foo"))
        assertFalse(isDataBindingExpression(""))
        assertFalse(isDataBindingExpression("foo"))
    }

    fun testDescribeCounts() {
        assertThat(describeCounts(0, 0, true, true)).isEqualTo("No errors or warnings")
        assertThat(describeCounts(0, 0, true, false)).isEqualTo("no errors or warnings")
        assertThat(describeCounts(0, 1, true, true)).isEqualTo("1 warning")
        assertThat(describeCounts(1, 0, true, true)).isEqualTo("1 error")
        assertThat(describeCounts(0, 2, true, true)).isEqualTo("2 warnings")
        assertThat(describeCounts(2, 0, true, true)).isEqualTo("2 errors")
        assertThat(describeCounts(2, 1, false, true)).isEqualTo("2 errors and 1 warning")
        assertThat(describeCounts(1, 2, false, true)).isEqualTo("1 error and 2 warnings")
        assertThat(describeCounts(5, 4, false, true)).isEqualTo("5 errors and 4 warnings")
        assertThat(describeCounts(2, 1, true, true)).isEqualTo("2 errors, 1 warning")
        assertThat(describeCounts(1, 2, true, true)).isEqualTo("1 error, 2 warnings")
        assertThat(describeCounts(5, 4, true, true)).isEqualTo("5 errors, 4 warnings")
    }

    fun testEndsWith() {
        assertTrue(endsWith("Foo", ""))
        assertTrue(endsWith("Foo", "o"))
        assertTrue(endsWith("Foo", "oo"))
        assertTrue(endsWith("Foo", "Foo"))
        assertTrue(endsWith("Foo", "FOO"))
        assertTrue(endsWith("Foo", "fOO"))

        assertFalse(endsWith("Foo", "f"))
    }

    fun testStartsWith() {
        assertTrue(startsWith("FooBar", "Bar", 3))
        assertTrue(startsWith("FooBar", "BAR", 3))
        assertTrue(startsWith("FooBar", "Foo", 0))
        assertFalse(startsWith("FooBar", "Foo", 2))
    }

    fun testIsXmlFile() {
        assertTrue(isXmlFile(File("foo.xml")))
        assertTrue(isXmlFile(File("foo.Xml")))
        assertTrue(isXmlFile(File("foo.XML")))

        assertFalse(isXmlFile(File("foo.png")))
        assertFalse(isXmlFile(File("xml")))
        assertFalse(isXmlFile(File("xml.png")))
    }

    fun testEditDistance() {
        assertEquals(0, editDistance("kitten", "kitten"))

        // editing kitten to sitting has edit distance 3:
        //   replace k with s
        //   replace e with i
        //   append g
        assertEquals(3, editDistance("kitten", "sitting"))

        assertEquals(3, editDistance("saturday", "sunday"))
        assertEquals(1, editDistance("button", "bitton"))
        assertEquals(6, editDistance("radiobutton", "bitton"))

        assertEquals(6, editDistance("radiobutton", "bitton", 10))
        assertEquals(6, editDistance("radiobutton", "bitton", 6))
        assertEquals(Integer.MAX_VALUE, editDistance("radiobutton", "bitton", 3))

        assertTrue(isEditableTo("radiobutton", "bitton", 10))
        assertTrue(isEditableTo("radiobutton", "bitton", 6))
        assertFalse(isEditableTo("radiobutton", "bitton", 3))
    }

    fun testSplitPath() {
        assertTrue(
            Arrays.equals(
                arrayOf("/foo", "/bar", "/baz"),
                Iterables.toArray(splitPath("/foo:/bar:/baz"), String::class.java)
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("/foo", "/bar"),
                Iterables.toArray(splitPath("/foo;/bar"), String::class.java)
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("/foo", "/bar:baz"),
                Iterables.toArray(splitPath("/foo;/bar:baz"), String::class.java)
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("\\foo\\bar", "\\bar\\foo"),
                Iterables.toArray(splitPath("\\foo\\bar;\\bar\\foo"), String::class.java)
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("\${sdk.dir}\\foo\\bar", "\\bar\\foo"),
                Iterables.toArray(
                    splitPath("\${sdk.dir}\\foo\\bar;\\bar\\foo"), String::class.java
                )
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("\${sdk.dir}/foo/bar", "/bar/foo"),
                Iterables.toArray(splitPath("\${sdk.dir}/foo/bar:/bar/foo"), String::class.java)
            )
        )

        assertTrue(
            Arrays.equals(
                arrayOf("C:\\foo", "/bar"),
                Iterables.toArray(splitPath("C:\\foo:/bar"), String::class.java)
            )
        )
    }

    fun testCommonParen1() {
        assertEquals(File("/a"), getCommonParent(File("/a/b/c/d/e"), File("/a/c")))
        assertEquals(File("/a"), getCommonParent(File("/a/c"), File("/a/b/c/d/e")))

        assertEquals(File("/"), getCommonParent(File("/foo/bar"), File("/bar/baz")))
        assertEquals(File("/"), getCommonParent(File("/foo/bar"), File("/")))
        assertNull(getCommonParent(File("C:\\Program Files"), File("F:\\")))
        assertNull(getCommonParent(File("C:/Program Files"), File("F:/")))

        assertEquals(
            File("/foo/bar/baz"),
            getCommonParent(File("/foo/bar/baz"), File("/foo/bar/baz"))
        )
        assertEquals(
            File("/foo/bar"),
            getCommonParent(File("/foo/bar/baz"), File("/foo/bar"))
        )
        assertEquals(
            File("/foo/bar"),
            getCommonParent(File("/foo/bar/baz"), File("/foo/bar/foo"))
        )
        assertEquals(File("/foo"), getCommonParent(File("/foo/bar"), File("/foo/baz")))
        assertEquals(File("/foo"), getCommonParent(File("/foo/bar"), File("/foo/baz")))
        assertEquals(
            File("/foo/bar"),
            getCommonParent(File("/foo/bar"), File("/foo/bar/baz"))
        )
    }

    fun testCommonParent2() {
        assertEquals(
            File("/"),
            getCommonParent(Arrays.asList(File("/foo/bar"), File("/bar/baz")))
        )
        assertEquals(
            File("/"), getCommonParent(Arrays.asList(File("/foo/bar"), File("/")))
        )
        assertNull(getCommonParent(Arrays.asList(File("C:\\Program Files"), File("F:\\"))))
        assertNull(getCommonParent(Arrays.asList(File("C:/Program Files"), File("F:/"))))

        assertEquals(
            File("/foo"),
            getCommonParent(Arrays.asList(File("/foo/bar"), File("/foo/baz")))
        )
        assertEquals(
            File("/foo"),
            getCommonParent(
                Arrays.asList(
                    File("/foo/bar"),
                    File("/foo/baz"),
                    File("/foo/baz/f")
                )
            )
        )
        assertEquals(
            File("/foo/bar"),
            getCommonParent(
                Arrays.asList(
                    File("/foo/bar"),
                    File("/foo/bar/baz"),
                    File("/foo/bar/foo2/foo3")
                )
            )
        )
    }

    fun testStripIdPrefix() {
        assertEquals("foo", stripIdPrefix("@+id/foo"))
        assertEquals("foo", stripIdPrefix("@id/foo"))
        assertEquals("foo", stripIdPrefix("foo"))
    }

    fun testIdReferencesMatch() {
        assertTrue(idReferencesMatch("@+id/foo", "@+id/foo"))
        assertTrue(idReferencesMatch("@id/foo", "@id/foo"))
        assertTrue(idReferencesMatch("@id/foo", "@+id/foo"))
        assertTrue(idReferencesMatch("@+id/foo", "@id/foo"))

        assertFalse(idReferencesMatch("@+id/foo", "@+id/bar"))
        assertFalse(idReferencesMatch("@id/foo", "@+id/bar"))
        assertFalse(idReferencesMatch("@+id/foo", "@id/bar"))
        assertFalse(idReferencesMatch("@+id/foo", "@+id/bar"))

        assertFalse(idReferencesMatch("@+id/foo", "@+id/foo1"))
        assertFalse(idReferencesMatch("@id/foo", "@id/foo1"))
        assertFalse(idReferencesMatch("@id/foo", "@+id/foo1"))
        assertFalse(idReferencesMatch("@+id/foo", "@id/foo1"))

        assertFalse(idReferencesMatch("@+id/foo1", "@+id/foo"))
        assertFalse(idReferencesMatch("@id/foo1", "@id/foo"))
        assertFalse(idReferencesMatch("@id/foo1", "@+id/foo"))
        assertFalse(idReferencesMatch("@+id/foo1", "@id/foo"))
    }

    fun testGetEncodedString() {
        checkEncoding("utf-8", false /*bom*/, "\n")
        checkEncoding("UTF-8", false /*bom*/, "\n")
        checkEncoding("UTF_16", false /*bom*/, "\n")
        checkEncoding("UTF-16", false /*bom*/, "\n")
        checkEncoding("UTF_16LE", false /*bom*/, "\n")

        // Try BOM's
        checkEncoding("utf-8", true /*bom*/, "\n")
        checkEncoding("UTF-8", true /*bom*/, "\n")
        checkEncoding("UTF_16", true /*bom*/, "\n")
        checkEncoding("UTF-16", true /*bom*/, "\n")
        checkEncoding("UTF_16LE", true /*bom*/, "\n")
        checkEncoding("UTF_32", true /*bom*/, "\n")
        checkEncoding("UTF_32LE", true /*bom*/, "\n")

        // Make sure this works for \r and \r\n as well
        checkEncoding("UTF-16", false /*bom*/, "\r")
        checkEncoding("UTF_16LE", false /*bom*/, "\r")
        checkEncoding("UTF-16", false /*bom*/, "\r\n")
        checkEncoding("UTF_16LE", false /*bom*/, "\r\n")
        checkEncoding("UTF-16", true /*bom*/, "\r")
        checkEncoding("UTF_16LE", true /*bom*/, "\r")
        checkEncoding("UTF_32", true /*bom*/, "\r")
        checkEncoding("UTF_32LE", true /*bom*/, "\r")
        checkEncoding("UTF-16", true /*bom*/, "\r\n")
        checkEncoding("UTF_16LE", true /*bom*/, "\r\n")
        checkEncoding("UTF_32", true /*bom*/, "\r\n")
        checkEncoding("UTF_32LE", true /*bom*/, "\r\n")
    }

    fun testGetLocale() {
        assertNull(getLocale(""))
        assertNull(getLocale("values"))
        assertNull(getLocale("values-xlarge-port"))
        assertEquals("en", getLocale("values-en")!!.language)
        assertEquals("pt", getLocale("values-pt-rPT-nokeys")!!.language)
        assertEquals("pt", getLocale("values-b+pt+PT-nokeys")!!.language)
        assertEquals("zh", getLocale("values-zh-rCN-keyshidden")!!.language)
    }

    @Suppress("JoinDeclarationAndAssignment")
    fun testGetLocale2() {
        var xml: LintDetectorTest.TestFile
        var context: XmlContext

        xml = TestFiles.xml("res/values/strings.xml", "<resources>\n</resources>\n")
        context = createXmlContext(xml.getContents(), File(xml.targetPath))
        assertNull(getLocale(context))
        dispose(context)

        xml = TestFiles.xml("res/values-no/strings.xml", "<resources>\n</resources>\n")
        context = createXmlContext(xml.getContents(), File(xml.targetPath))
        assertEquals("no", getLocale(context)!!.language)
        dispose(context)

        xml = TestFiles.xml(
            "res/values/strings.xml",
            "" +
                "<resources tools:locale=\"nb\" xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                "</resources>\n"
        )
        context = createXmlContext(xml.getContents(), File(xml.targetPath))
        assertEquals("nb", getLocale(context)!!.language)
        dispose(context)

        // tools:locale wins over folder location
        xml = TestFiles.xml(
            "res/values-fr/strings.xml",
            "" +
                "<resources tools:locale=\"nb\" xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                "</resources>\n"
        )
        context = createXmlContext(xml.getContents(), File(xml.targetPath))
        assertEquals("nb", getLocale(context)!!.language)
        dispose(context)

        UastEnvironment.disposeApplicationEnvironment()
    }

    private fun dispose(context: XmlContext) {
        (context.project.client as? LintCliClient)?.disposeProjects(listOf(context.project))
    }

    fun testGetLocaleAndRegion() {
        assertNull(getLocaleAndRegion(""))
        assertNull(getLocaleAndRegion("values"))
        assertNull(getLocaleAndRegion("values-xlarge-port"))
        assertEquals("en", getLocaleAndRegion("values-en"))
        assertEquals("pt-rPT", getLocaleAndRegion("values-pt-rPT-nokeys"))
        assertEquals("b+pt+PT", getLocaleAndRegion("values-b+pt+PT-nokeys"))
        assertEquals("zh-rCN", getLocaleAndRegion("values-zh-rCN-keyshidden"))
        assertEquals("ms", getLocaleAndRegion("values-ms-keyshidden"))
    }

    fun testComputeResourceName() {
        assertEquals("", computeResourceName("", "", null))
        assertEquals("foo", computeResourceName("", "foo", null))
        assertEquals("foo", computeResourceName("foo", "", null))
        assertEquals("prefix_name", computeResourceName("prefix_", "name", null))
        assertEquals("prefixName", computeResourceName("prefix", "name", null))
        assertEquals("PrefixName", computeResourceName("prefix", "Name", null))
        assertEquals("PrefixName", computeResourceName("prefix_", "Name", null))
        assertEquals("MyPrefixName", computeResourceName("myPrefix", "Name", null))
        assertEquals(
            "my_prefix_name",
            computeResourceName("myPrefix", "name", ResourceFolderType.LAYOUT)
        )
        assertEquals(
            "UnitTestPrefixContentFrame",
            computeResourceName(
                "unit_test_prefix_", "ContentFrame", ResourceFolderType.VALUES
            )
        )
        assertEquals(
            "MyPrefixMyStyle",
            computeResourceName("myPrefix_", "MyStyle", ResourceFolderType.VALUES)
        )
    }

    fun testIsModelOlderThan() {
        var project = mock(Project::class.java)
        `when`<GradleVersion>(project.gradleModelVersion).thenReturn(GradleVersion.parse("0.10.4"))

        assertTrue(isModelOlderThan(project, 0, 10, 5))
        assertTrue(isModelOlderThan(project, 0, 11, 0))
        assertTrue(isModelOlderThan(project, 0, 11, 4))
        assertTrue(isModelOlderThan(project, 1, 0, 0))

        project = mock(Project::class.java)
        `when`<GradleVersion>(project.gradleModelVersion).thenReturn(GradleVersion.parse("0.11.0"))

        assertTrue(isModelOlderThan(project, 1, 0, 0))
        assertFalse(isModelOlderThan(project, 0, 11, 0))
        assertFalse(isModelOlderThan(project, 0, 10, 4))

        project = mock(Project::class.java)
        `when`<GradleVersion>(project.gradleModelVersion).thenReturn(GradleVersion.parse("0.11.5"))

        assertTrue(isModelOlderThan(project, 1, 0, 0))
        assertFalse(isModelOlderThan(project, 0, 11, 0))

        project = mock(Project::class.java)
        `when`<GradleVersion>(project.gradleModelVersion).thenReturn(GradleVersion.parse("1.0.0"))

        assertTrue(isModelOlderThan(project, 1, 0, 1))
        assertFalse(isModelOlderThan(project, 1, 0, 0))
        assertFalse(isModelOlderThan(project, 0, 11, 0))

        project = mock(Project::class.java)
        assertTrue(isModelOlderThan(project, 0, 0, 0, true))
        assertFalse(isModelOlderThan(project, 0, 0, 0, false))
    }

    private class DefaultApiVersion(private val mApiLevel: Int, private val mCodename: String?) :
        ApiVersion {

        override fun getApiLevel(): Int {
            return mApiLevel
        }

        override fun getCodename(): String? {
            return mCodename
        }

        override fun getApiString(): String {
            fail("Not needed in this test")
            return "<invalid>"
        }
    }

    fun testFindSubstring() {
        assertEquals("foo", findSubstring("foo", null, null))
        assertEquals("foo", findSubstring("foo  ", null, "  "))
        assertEquals("foo", findSubstring("  foo", "  ", null))
        assertEquals("foo", findSubstring("[foo]", "[", "]"))
    }

    fun testGetFormattedParameters() {
        assertEquals(
            Arrays.asList("foo", "bar"),
            getFormattedParameters(
                "Prefix %1\$s Divider %2\$s Suffix", "Prefix foo Divider bar Suffix"
            )
        )
    }

    fun testEscapePropertyValue() {
        assertEquals("foo", escapePropertyValue("foo"))
        assertEquals("\\  foo  ", escapePropertyValue("  foo  "))
        assertEquals("c\\:/foo/bar", escapePropertyValue("c:/foo/bar"))
        assertEquals("\\!\\#\\:\\\\a\\\\b\\\\c", escapePropertyValue("!#:\\a\\b\\c"))
        assertEquals(
            "foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo\\#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo",
            escapePropertyValue(
                "foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo"
            )
        )
    }

    fun testGetAutoBoxedType() {
        assertEquals(TYPE_INTEGER_WRAPPER, getAutoBoxedType(TYPE_INT))
        assertEquals(TYPE_INT, getPrimitiveType(TYPE_INTEGER_WRAPPER))

        val pairs = arrayOf(
            TYPE_BOOLEAN,
            TYPE_BOOLEAN_WRAPPER,
            TYPE_BYTE,
            TYPE_BYTE_WRAPPER,
            TYPE_CHAR,
            TYPE_CHARACTER_WRAPPER,
            TYPE_DOUBLE,
            TYPE_DOUBLE_WRAPPER,
            TYPE_FLOAT,
            TYPE_FLOAT_WRAPPER,
            TYPE_INT,
            TYPE_INTEGER_WRAPPER,
            TYPE_LONG,
            TYPE_LONG_WRAPPER,
            TYPE_SHORT,
            TYPE_SHORT_WRAPPER
        )

        var i = 0
        while (i < pairs.size) {
            val primitive = pairs[i]
            val autoBoxed = pairs[i + 1]
            assertEquals(autoBoxed, getAutoBoxedType(primitive))
            assertEquals(primitive, getPrimitiveType(autoBoxed))
            i += 2
        }
    }

    fun testResolveManifestName() {
        assertEquals(
            "test.pkg.TestActivity",
            resolveManifestName(
                getElementWithNameValue(
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    package=\"test.pkg\">\n" +
                        "    <application>\n" +
                        "        <activity android:name=\".TestActivity\" />\n" +
                        "    </application>\n" +
                        "</manifest>\n",
                    ".TestActivity"
                )
            )
        )

        assertEquals(
            "test.pkg.TestActivity",
            resolveManifestName(
                getElementWithNameValue(
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    package=\"test.pkg\">\n" +
                        "    <application>\n" +
                        "        <activity android:name=\"TestActivity\" />\n" +
                        "    </application>\n" +
                        "</manifest>\n",
                    "TestActivity"
                )
            )
        )

        assertEquals(
            "test.pkg.TestActivity",
            resolveManifestName(
                getElementWithNameValue(
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    package=\"test.pkg\">\n" +
                        "    <application>\n" +
                        "        <activity android:name=\"test.pkg.TestActivity\" />\n" +
                        "    </application>\n" +
                        "</manifest>\n",
                    "test.pkg.TestActivity"
                )
            )
        )

        assertEquals(
            "test.pkg.TestActivity.Bar",
            resolveManifestName(
                getElementWithNameValue(
                    "" +
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    package=\"test.pkg\">\n" +
                        "    <application>\n" +
                        "        <activity android:name=\"test.pkg.TestActivity\$Bar\" />\n" +
                        "    </application>\n" +
                        "</manifest>\n",
                    "test.pkg.TestActivity\$Bar"
                )
            )
        )
    }

    fun testGetFileNameWithParent() {
        assertThat(
            getFileNameWithParent(
                TestLintClient(),
                File(
                    "tmp" +
                        File.separator +
                        "foo" +
                        File.separator +
                        "bar.baz"
                )
            )
        )
            .isEqualTo("foo/bar.baz")
        assertThat(
            getFileNameWithParent(
                LintCliClient(CLIENT_UNIT_TESTS),
                File(
                    "tmp" +
                        File.separator +
                        "foo" +
                        File.separator +
                        "bar.baz"
                )
            )
        )
            .isEqualTo(if (File.separatorChar == '/') "foo/bar.baz" else "foo\\\\bar.baz")
    }

    fun testJavaKeyword() {
        assertThat(isJavaKeyword("")).isFalse()
        assertThat(isJavaKeyword("iff")).isFalse()
        assertThat(isJavaKeyword("if")).isTrue()
        assertThat(isJavaKeyword("true")).isTrue()
        assertThat(isJavaKeyword("false")).isTrue()
    }

    private class JavaTestContext(
        driver: LintDriver,
        project: Project,
        private val mJavaSource: String,
        file: File
    ) : JavaContext(driver, project, null, file) {

        override fun getContents(): String? {
            return mJavaSource
        }
    }

    private class XmlTestContext(
        driver: LintDriver,
        project: Project,
        private val xmlSource: String,
        file: File,
        type: ResourceFolderType,
        parser: XmlParser,
        document: Document
    ) : XmlContext(driver, project, null, file, type, parser, xmlSource, document) {

        override fun getContents(): String? {
            return xmlSource
        }
    }

    companion object {
        private fun checkEncoding(encoding: String, writeBom: Boolean, lineEnding: String) {
            val sb = StringBuilder()

            // Norwegian extra vowel characters such as "latin small letter a with ring above"
            val value = "\u00e6\u00d8\u00e5"
            val expected = (
                "First line." +
                    lineEnding +
                    "Second line." +
                    lineEnding +
                    "Third line." +
                    lineEnding +
                    value +
                    lineEnding
                )
            sb.append(expected)
            val file = File.createTempFile("getEncodingTest$encoding$writeBom", ".txt")
            file.deleteOnExit()
            val stream = BufferedOutputStream(FileOutputStream(file))
            val writer = OutputStreamWriter(stream, encoding)

            if (writeBom) {
                val normalized = encoding.toLowerCase(Locale.US).replace("-", "_")
                when (normalized) {
                    "utf_8" -> {
                        stream.write(0xef)
                        stream.write(0xbb)
                        stream.write(0xbf)
                    }
                    "utf_16" -> {
                        stream.write(0xfe)
                        stream.write(0xff)
                    }
                    "utf_16le" -> {
                        stream.write(0xff)
                        stream.write(0xfe)
                    }
                    "utf_32" -> {
                        stream.write(0x0)
                        stream.write(0x0)
                        stream.write(0xfe)
                        stream.write(0xff)
                    }
                    "utf_32le" -> {
                        stream.write(0xff)
                        stream.write(0xfe)
                        stream.write(0x0)
                        stream.write(0x0)
                    }
                    else -> fail("Can't write BOM for encoding $encoding")
                }
            }
            writer.write(sb.toString())
            writer.close()

            val s = getEncodedString(LintCliClient(CLIENT_UNIT_TESTS), file, true).toString()
            assertEquals(expected, s)

            val seq = getEncodedString(LintCliClient(CLIENT_UNIT_TESTS), file, false)
            if (encoding.equals("utf-8", ignoreCase = true)) {
                assertFalse(seq is String)
            }
            assertEquals(expected, seq.toString())
        }

        private fun createTestProjectForFile(
            dir: File,
            relativePath: File,
            source: String,
            libs: List<File> = emptyList(),
            library: Boolean = false,
            android: Boolean = true,
            javaLanguageLevel: LanguageLevel? = null,
            kotlinLanguageLevel: LanguageVersionSettings? = null
        ): Project {
            val fullPath = File(dir, relativePath.path)
            fullPath.parentFile.mkdirs()
            try {
                Files.asCharSink(fullPath, Charsets.UTF_8).write(source)
            } catch (e: IOException) {
                fail(e.message)
            }

            val client = object : LintCliClient(CLIENT_UNIT_TESTS) {
                override fun readFile(file: File): CharSequence {
                    return if (file.path == fullPath.path) {
                        source
                    } else super.readFile(file)
                }

                override fun getCompileTarget(project: Project): IAndroidTarget? {
                    if (!android) {
                        return null
                    }
                    val targets = getTargets()
                    for (i in targets.indices.reversed()) {
                        val target = targets[i]
                        if (target.isPlatform) {
                            return target
                        }
                    }

                    return super.getCompileTarget(project)
                }

                override fun getJavaSourceFolders(project: Project): List<File> {
                    return listOf(dir)
                }

                override fun getSdkHome(): File {
                    return TestUtils.getSdk()
                }

                override fun getJavaLanguageLevel(project: Project): LanguageLevel {
                    if (javaLanguageLevel != null) {
                        return javaLanguageLevel
                    }
                    return super.getJavaLanguageLevel(project)
                }

                override fun getKotlinLanguageLevel(project: Project): LanguageVersionSettings {
                    if (kotlinLanguageLevel != null) {
                        return kotlinLanguageLevel
                    }
                    return super.getKotlinLanguageLevel(project)
                }

                override fun createProject(dir: File, referenceDir: File): Project {
                    val clone = super.createProject(dir, referenceDir)
                    val p = object : TestLintClient.TestProject(this, dir, referenceDir, null, null) {
                        override fun isLibrary(): Boolean {
                            return library
                        }
                        override fun isAndroidProject(): Boolean {
                            return android
                        }
                    }
                    p.buildTargetHash = clone.buildTargetHash
                    p.ideaProject = clone.ideaProject
                    return p
                }

                override fun getJavaLibraries(
                    project: Project,
                    includeProvided: Boolean
                ): List<File> {
                    return libs + findKotlinStdlibPath().map { File(it) }
                }
            }
            val project = client.getProject(dir, dir)
            client.initializeProjects(listOf(project))
            return project
        }

        private fun createTestProjectForFiles(
            dir: File,
            sourcesMap: Map<File, String>,
            libs: List<File> = emptyList()
        ): Project {
            sourcesMap.forEach { fullPath, source ->
                fullPath.parentFile.mkdirs()
                try {
                    Files.asCharSink(fullPath, Charsets.UTF_8).write(source)
                } catch (e: IOException) {
                    fail(e.message)
                }
            }

            val client = object : LintCliClient(CLIENT_UNIT_TESTS) {
                override fun readFile(file: File): CharSequence {
                    return if (file in sourcesMap) {
                        sourcesMap[file] as CharSequence
                    } else super.readFile(file)
                }

                override fun getCompileTarget(project: Project): IAndroidTarget? {
                    val targets = getTargets()
                    for (i in targets.indices.reversed()) {
                        val target = targets[i]
                        if (target.isPlatform) {
                            return target
                        }
                    }

                    return super.getCompileTarget(project)
                }

                override fun getSdkHome(): File {
                    return TestUtils.getSdk()
                }

                override fun getJavaLibraries(
                    project: Project,
                    includeProvided: Boolean
                ): List<File> {
                    return libs + findKotlinStdlibPath().map { File(it) }
                }

                override fun getJavaSourceFolders(project: Project): List<File> {
                    // Include the top-level dir as a source root, so Java references are resolved.
                    return super.getJavaSourceFolders(project) + dir
                }
            }
            val project = client.getProject(dir, dir)
            client.initializeProjects(listOf(project))
            return project
        }

        fun createXmlContext(@Language("XML") xml: String?, relativePath: File): XmlContext {
            val dir = File(System.getProperty("java.io.tmpdir"))
            val fullPath = File(dir, relativePath.path)
            val project = createTestProjectForFile(dir, relativePath, xml!!)
            val client = project.getClient() as LintCliClient

            val request = LintRequest(client, listOf(fullPath))
            val driver = LintDriver(TestIssueRegistry(), LintCliClient(CLIENT_UNIT_TESTS), request)
            driver.scope = Scope.JAVA_FILE_SCOPE
            val folderType = ResourceFolderType.getFolderType(relativePath.parentFile.name)

            val parser = client.xmlParser
            val document = parser.parseXml(xml, fullPath)
            return XmlTestContext(driver, project, xml, fullPath, folderType!!, parser, document!!)
        }

        @JvmStatic
        fun parse(
            @Language("JAVA") javaSource: String,
            relativePath: File?
        ): Pair<JavaContext, Disposable> {
            var path = relativePath
            if (path == null) {
                val className = ClassName(javaSource)
                val pkg = className.packageName
                val name = className.className
                assert(pkg != null)
                assert(name != null)
                path = File(
                    "src" +
                        File.separatorChar +
                        pkg!!.replace('.', File.separatorChar) +
                        File.separatorChar +
                        name +
                        DOT_JAVA
                )
            }

            return parse(java(path.path, javaSource))
        }

        @JvmStatic
        fun parseKotlin(
            @Language("Kt") kotlinSource: String,
            relativePath: File?
        ): Pair<JavaContext, Disposable> {
            var path = relativePath
            if (path == null) {
                val className = ClassName(kotlinSource)
                val pkg = className.packageName
                val name = className.className
                assert(pkg != null)
                assert(name != null)
                path = File(
                    "src" +
                        File.separatorChar +
                        pkg!!.replace('.', File.separatorChar) +
                        File.separatorChar +
                        name +
                        DOT_KT
                )
            }

            return parse(kotlin(path.path, kotlinSource))
        }

        @JvmStatic
        fun parse(vararg testFiles: TestFile): Pair<JavaContext, Disposable> {
            return parse(testFiles = *testFiles, javaLanguageLevel = null)
        }

        @JvmStatic
        fun parse(
            vararg testFiles: TestFile = emptyArray(),
            javaLanguageLevel: LanguageLevel? = null,
            kotlinLanguageLevel: LanguageVersionSettings? = null,
            library: Boolean = false,
            android: Boolean = true
        ): Pair<JavaContext, Disposable> {
            val temp = Files.createTempDir()
            val dir = File(temp, "src")
            dir.mkdir()

            val libs: List<File> = if (testFiles.size > 1) {
                val projects = TestLintTask().files(*testFiles).createProjects(dir)
                testFiles.filter { it.targetRelativePath.endsWith(DOT_JAR) }
                    .map { File(projects[0], it.targetRelativePath) }
            } else {
                emptyList()
            }

            val primary = testFiles[0]
            val relativePath = File(primary.targetRelativePath)
            val source = primary.getContents()!!

            val fullPath = File(dir, relativePath.path)
            val project = createTestProjectForFile(
                dir, relativePath, source, libs,
                library, android, javaLanguageLevel, kotlinLanguageLevel
            )
            val client = project.getClient() as LintCliClient
            val request = LintRequest(client, listOf(fullPath))

            val driver = LintDriver(TestIssueRegistry(), LintCliClient(CLIENT_UNIT_TESTS), request)
            driver.scope = Scope.JAVA_FILE_SCOPE
            val context = JavaTestContext(driver, project, source, fullPath)
            val uastParser = client.getUastParser(project)
            assertNotNull(uastParser)
            context.uastParser = uastParser
            uastParser.prepare(listOf(context), javaLanguageLevel, kotlinLanguageLevel)
            val uFile = uastParser.parse(context)
            context.uastFile = uFile
            assert(uFile != null)
            context.setJavaFile(uFile!!.psi)
            val disposable = Disposable {
                client.disposeProjects(listOf(project))
                temp.deleteRecursively()
            }
            return Pair.of<JavaContext, Disposable>(context, disposable)
        }

        @JvmStatic
        fun parseAll(vararg testFiles: TestFile): Pair<List<JavaContext>, Disposable> {
            val temp = Files.createTempDir()
            val dir = File(temp, "src")
            dir.mkdir()

            val libs: List<File> = if (testFiles.size > 1) {
                val projects = TestLintTask().files(*testFiles).createProjects(dir)
                testFiles.filter { it.targetRelativePath.endsWith(DOT_JAR) }
                    .map { File(projects[0], it.targetRelativePath) }
            } else {
                emptyList()
            }

            val sources = testFiles
                .filter { !it.targetRelativePath.endsWith(DOT_JAR) }
                .associate { Pair(File(dir, it.targetRelativePath), it.contents) }

            val project = createTestProjectForFiles(dir, sources, libs)
            val client = project.getClient() as LintCliClient
            val request = LintRequest(client, sources.keys.toList())
            val driver = LintDriver(TestIssueRegistry(), LintCliClient(CLIENT_UNIT_TESTS), request)
            driver.scope = EnumSet.of(Scope.ALL_JAVA_FILES)

            val uastParser = client.getUastParser(project)
            assertNotNull(uastParser)
            val contexts = sources.map { (fullPath, source) ->
                val context = JavaTestContext(driver, project, source, fullPath)
                context.uastParser = uastParser
                context
            }
            uastParser.prepare(contexts)
            contexts.forEach { context ->
                val uFile = uastParser.parse(context)
                context.uastFile = uFile
                assert(uFile != null)
                context.setJavaFile(uFile!!.sourcePsi)
            }

            val disposable = Disposable {
                client.disposeProjects(listOf(project))
                temp.deleteRecursively()
            }
            return Pair.of<List<JavaContext>, Disposable>(contexts, disposable)
        }

        private fun getElementWithNameValue(
            @Language("XML") xml: String,
            activityName: String
        ): Element {
            val document = XmlUtils.parseDocumentSilently(xml, true)
            assertNotNull(document)
            val root = document!!.documentElement
            assertNotNull(root)
            for (application in getChildren(root)) {
                for (element in getChildren(application)) {
                    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (activityName == name) {
                        return element
                    }
                }
            }

            fail("Didn't find $activityName")
            throw AssertionError("Didn't find $activityName")
        }
    }
}
