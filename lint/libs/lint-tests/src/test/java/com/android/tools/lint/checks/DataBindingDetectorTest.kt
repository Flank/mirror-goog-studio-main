/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.lint.LintCliXmlParser
import com.android.tools.lint.client.api.XmlParser
import com.android.tools.lint.detector.api.Detector
import org.w3c.dom.Document
import java.io.File

class DataBindingDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector {
        return DataBindingDetector()
    }

    fun testCharacterEscaped() {
        lint().allowCompilationErrors().files(
            xml(
                "res/layout/layout1.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "        xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                    "  <data>\n" +
                    "    <import type=\"test.langdb.calc.Calculator.Op\" />\n" +
                    "    <variable\n" +
                    "        name=\"calc\"\n" +
                    "        type=\"test.langdb.calc.Calculator&lt;String>\" />\n" +
                    "  </data>\n" +
                    "\n" +
                    "  <androidx.constraintlayout.widget.ConstraintLayout\n" +
                    "      android:layout_width=\"match_parent\"\n" +
                    "      android:layout_height=\"match_parent\">\n" +
                    "  <TextView\n" +
                    "      android:id=\"@+id/view_id\"\n" +
                    "      android:text=\"@{calc.a &lt; (calc.b &lt;&lt; 2) ? calc.textA : calc.textB}\"/>\n" +
                    "  </androidx.constraintlayout.widget.ConstraintLayout>\n" +
                    "</layout>\n" +
                    "\n"
            )
        ).run().expectClean()
    }

    fun testCharacterNotEscaped() {
        // In this test the XML isn't valid and normally the XML parser will refuse to
        // parse it, but in the IDE things work since there's a more fault tolerant parser
        // there intended for interactive editing. To make tests work, we'll provide a
        // custom version of the parser which on the fly replaces "  <  " with "&lt; " before
        // parsing the input. These two have the same length, which is important for ensuring
        // that offsets are identical.
        // (This means that this lint check won't work from Gradle/on the command line,
        // but that's okay; this check is intended primarily for in-IDE usage.)
        val client = object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
            override val xmlParser: XmlParser = object : LintCliXmlParser(this) {
                override fun parseXml(xml: CharSequence, file: File): Document? {
                    val fixedXml = xml.toString().replace("  <  ", "&lt; ")
                    return super.parseXml(fixedXml, file)
                }
            }
        }

        val expected = "" +
            "res/layout/layout1.xml:8: Error: < must be escaped (as &lt;) in attribute values [XmlEscapeNeeded]\n" +
            "        type=\"test.langdb.calc.Calculator  <  String>\" />\n" +
            "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/layout/layout1.xml:16: Error: < must be escaped (as &lt;) in attribute values [XmlEscapeNeeded]\n" +
            "      android:text=\"@{calc.a  <  calc.b ? calc.textA : calc.textB}\"/>\n" +
            "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "2 errors, 0 warnings"
        lint().allowCompilationErrors().files(
            xml(
                "res/layout/layout1.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "        xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                    "  <data>\n" +
                    "    <import type=\"test.langdb.calc.Calculator.Op\" />\n" +
                    "    <variable\n" +
                    "        name=\"calc\"\n" +
                    // Note special extra spacing around "<": this is there for the
                    // string replacement to find and have enough space to replace it
                    // with &lt;
                    "        type=\"test.langdb.calc.Calculator  <  String>\" />\n" +
                    "  </data>\n" +
                    "\n" +
                    "  <androidx.constraintlayout.widget.ConstraintLayout\n" +
                    "      android:layout_width=\"match_parent\"\n" +
                    "      android:layout_height=\"match_parent\">\n" +
                    "  <TextView\n" +
                    "      android:id=\"@+id/view_id\"\n" +
                    "      android:text=\"@{calc.a  <  calc.b ? calc.textA : calc.textB}\"/>\n" +
                    "  </androidx.constraintlayout.widget.ConstraintLayout>\n" +
                    "</layout>\n" +
                    "\n"
            )
        ).allowCompilationErrors().client(client).run().expect(expected).expectFixDiffs(
            "Fix for res/layout/layout1.xml line 8: Change '<' to '&lt;':\n" +
                "@@ -8 +8\n" +
                "-         type=\"test.langdb.calc.Calculator  <  String>\" />\n" +
                "+         type=\"test.langdb.calc.Calculator  &lt;  String>\" />\n" +
                "Fix for res/layout/layout1.xml line 16: Change '<' to '&lt;':\n" +
                "@@ -16 +16\n" +
                "-       android:text=\"@{calc.a  <  calc.b ? calc.textA : calc.textB}\"/>\n" +
                "+       android:text=\"@{calc.a  &lt;  calc.b ? calc.textA : calc.textB}\"/>"
        )
    }
}
