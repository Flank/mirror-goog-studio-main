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

package com.android.tools.lint.detector.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.LintFix.ReplaceString
import com.android.tools.lint.detector.api.LintFix.SetAttribute
import com.google.common.collect.Maps
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiMethod
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UCallExpression
import java.math.BigDecimal
import java.util.ArrayList
import java.util.HashMap
import java.util.regex.Pattern

class LintFixTest : TestCase() {
    fun testBasic() {
        val builder = LintFix.create().map("foo", 3, BigDecimal(50))
        builder.put("name1", "Name1")
        builder.put("name2", "Name2")
        builder.put(Maps.newHashMap<Any, Any>())
        builder.put(ArrayList<Any>())
        builder.put<Any>(null) // no-op
        val quickfixData = builder.build() as LintFix.DataMap
        assertThat(quickfixData.get(Float::class.java)).isNull()
        assertThat(quickfixData.get(String::class.java)).isEqualTo("foo")
        assertThat(quickfixData.get(Int::class.java)).isEqualTo(3)
        assertThat(quickfixData.get(BigDecimal::class.java)).isEqualTo(BigDecimal(50))
        assertThat(quickfixData.get("name1")).isEqualTo("Name1")
        assertThat(quickfixData.get("name2")).isEqualTo("Name2")

        var foundString = false
        var foundInteger = false
        var foundBigDecimal = false
        for (data in quickfixData) { // no order guarantee
            when (data) {
                "foo" -> foundString = true
                3 -> foundInteger = true
                is BigDecimal -> foundBigDecimal = true
            }
        }
        assertThat(foundString).isTrue()
        assertThat(foundInteger).isTrue()
        assertThat(foundBigDecimal).isTrue()

        assertNotNull(LintFix.getData(quickfixData, BigDecimal::class.java))

        // Check key conversion to general interface
        assertNull(LintFix.getData(quickfixData, ArrayList::class.java))
        assertNotNull(LintFix.getData(quickfixData, List::class.java))
        assertNull(LintFix.getData(quickfixData, HashMap::class.java))
        assertNotNull(LintFix.getData(quickfixData, Map::class.java))
    }

    fun testClassInheritance() {

        val builder = LintFix.create().map(Integer.valueOf(5))
        val quickfixData = builder.build() as LintFix.DataMap
        assertThat(quickfixData.get(Int::class.java)).isEqualTo(5)
        // Looking up with a more general class:
        assertThat(quickfixData.get(Number::class.java)).isEqualTo(5)
    }

    fun testSetAttribute() {
        val fixData = LintFix.create().set().namespace("namespace")
            .attribute("attribute").value("value").build() as SetAttribute
        assertThat(fixData.namespace).isEqualTo("namespace")
        assertThat(fixData.attribute).isEqualTo("attribute")
        assertThat(fixData.value).isEqualTo("value")
    }

    fun testReplaceString() {
        var fixData = LintFix.create().replace().text("old")
            .with("new").build() as ReplaceString
        assertThat(fixData.oldString).isEqualTo("old")
        assertThat(fixData.replacement).isEqualTo("new")
        assertThat(fixData.oldPattern).isNull()

        fixData = LintFix.create().replace().pattern("(oldPattern)")
            .with("new").build() as ReplaceString
        assertThat(fixData.oldPattern).isEqualTo("(oldPattern)")
        assertThat(fixData.replacement).isEqualTo("new")
        assertThat(fixData.oldString).isNull()

        fixData = LintFix.create().replace().pattern("oldPattern").with("new")
            .build() as ReplaceString
        assertThat(fixData.oldPattern).isEqualTo("(oldPattern)")
        assertThat(fixData.replacement).isEqualTo("new")
        assertThat(fixData.oldString).isNull()
    }

    fun testGroupMatching() {
        val fix = LintFix.create().replace().pattern("abc\\((\\d+)\\)def")
            .with("Number was \\k<1>! I said \\k<1>!").build() as ReplaceString
        assertTrue(fix.oldPattern != null)
        val matcher = Pattern.compile(fix.oldPattern).matcher("abc(42)def")
        assertTrue(matcher.matches())
        val expanded = fix.expandBackReferences(matcher)
        assertEquals("Number was 42! I said 42!", expanded)
    }

    fun testMatching() {
        lint().files(
            java(
                "" +
                    "package test.pkg;\n" +
                    "import android.util.Log;\n" +
                    "public class Test {\n" +
                    "    void test() {\n" +
                    "        Log.d(\"TAG\", \"msg\");\n" +
                    "    }\n" +
                    "}"
            )
        )
            .detector(SampleTestDetector())
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                "src/test/pkg/Test.java:5: Warning: Sample test message [TestIssueId]\n" +
                    "        Log.d(\"TAG\", \"msg\");\n" +
                    "        ~~~\n" +
                    "0 errors, 1 warnings\n"
            )
            .verifyFixes().window(1).expectFixDiffs(
                "" +
                    "Fix for src/test/pkg/Test.java line 4: Fix Description:\n" +
                    "@@ -5 +5\n" +
                    "      void test() {\n" +
                    "-         Log.d(\"TAG\", \"msg\");\n" +
                    "+         MyLogger.d(\"msg\"); // Was: Log.d(\"TAG\", \"msg\");\n" +
                    "      }\n"
            )
    }

    /**
     * Detector which makes use of a couple of lint fix string replacement features:
     * (1) ranges (larger than error range, and (2) back references
     */
    class SampleTestDetector : Detector(), SourceCodeScanner {
        override fun getApplicableMethodNames(): List<String> {
            return listOf("d")
        }

        override fun visitMethodCall(
            context: JavaContext,
            node: UCallExpression,
            method: PsiMethod
        ) {
            val evaluator = context.evaluator

            if (evaluator.isMemberInClass(method, "android.util.Log")) {
                val source = node.asSourceString()
                @Language("RegExp")
                val oldPattern = "(${Pattern.quote(source)})"
                val receiver = node.receiver!!
                var replacement = source.replace(
                    receiver.asSourceString(),
                    "MyLogger"
                ) + "; // Was: \\k<1>"
                replacement = replacement.replace("\"TAG\", ", "")

                val fix = LintFix.create()
                    .name("Fix Description")
                    .replace().pattern(oldPattern).with(replacement)
                    .range(context.getLocation(node))
                    .shortenNames()
                    .build()
                context.report(
                    SAMPLE_ISSUE, context.getLocation(receiver),
                    "Sample test message", fix
                )
            }
        }

        companion object {
            val SAMPLE_ISSUE = Issue.create(
                "TestIssueId", "Not applicable", "Not applicable",
                Category.MESSAGES, 5, Severity.WARNING,
                Implementation(
                    SampleTestDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
        }
    }
}
