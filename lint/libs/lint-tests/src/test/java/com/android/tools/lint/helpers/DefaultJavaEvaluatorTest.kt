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

package com.android.tools.lint.helpers

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.asRecursiveLogString
import org.junit.Test

/**
 * Most of the evaluator is tested indirectly via all the lint unit
 * tests; this covers some additional specific scenarios.
 */
class DefaultJavaEvaluatorTest {
    // Regression test for https://groups.google.com/d/msg/lint-dev/BaRimyf40tI/DpkOjMMEAQAJ
    @Test
    fun lookUpAnnotationsOnUastModifierLists() {
        lint().files(
            java(
                """
                    package foo;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class MyTest {
                        public void myTest(@Override int something) { }
                    }"""
            ).indented()
        )
            .sdkHome(TestUtils.getSdk().toFile())
            .issues(TestAnnotationLookupDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testCallAssignmentRanges() {
        lint().files(
            java(
                """
                package foo;
                public class Bean {
                    public String getFoo() { return ""; }
                    public void setFoo(String foo) {
                    }
                }
                """
            ).indented(),
            kotlin(
                """
                package foo
                fun test(s: String) {
                    val bean = Bean()
                    bean.setFoo("value")
                    bean.foo = "value"
                    bean.foo = "valu" + 'e'
                    bean.foo = ""${"\""}value""${"\""}
                    bean.foo = s.toString()
                    bean.foo = s
                }
                """
            ).indented()
        )
            .sdkHome(TestUtils.getSdk().toFile())
            .issues(TestAnnotationLookupDetector.ISSUE)
            .run()
            .expect(
                """
                src/foo/test.kt:4: Warning: Error with arguments but no receiver [Order]
                    bean.setFoo("value")
                         ~~~~~~~~~~~~~~~
                src/foo/test.kt:4: Warning: Error with receiver and arguments [Order]
                    bean.setFoo("value")
                    ~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:4: Warning: Error with receiver and no arguments [Order]
                    bean.setFoo("value")
                    ~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with arguments but no receiver [Order]
                    bean.foo = "value"
                         ~~~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with receiver and arguments [Order]
                    bean.foo = "value"
                    ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:5: Warning: Error with receiver and no arguments [Order]
                    bean.foo = "value"
                    ~~~~~~~~
                src/foo/test.kt:6: Warning: Error with arguments but no receiver [Order]
                    bean.foo = "valu" + 'e'
                         ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:6: Warning: Error with receiver and arguments [Order]
                    bean.foo = "valu" + 'e'
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:6: Warning: Error with receiver and no arguments [Order]
                    bean.foo = "valu" + 'e'
                    ~~~~~~~~
                src/foo/test.kt:7: Warning: Error with arguments but no receiver [Order]
                    bean.foo = ""${'"'}value""${'"'}
                         ~~~~~~~~~~~~~~~~~
                src/foo/test.kt:7: Warning: Error with receiver and arguments [Order]
                    bean.foo = ""${'"'}value""${'"'}
                    ~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:7: Warning: Error with receiver and no arguments [Order]
                    bean.foo = ""${'"'}value""${'"'}
                    ~~~~~~~~
                src/foo/test.kt:8: Warning: Error with arguments but no receiver [Order]
                    bean.foo = s.toString()
                         ~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:8: Warning: Error with receiver and arguments [Order]
                    bean.foo = s.toString()
                    ~~~~~~~~~~~~~~~~~~~~~~~
                src/foo/test.kt:8: Warning: Error with receiver and no arguments [Order]
                    bean.foo = s.toString()
                    ~~~~~~~~
                src/foo/test.kt:9: Warning: Error with arguments but no receiver [Order]
                    bean.foo = s
                         ~~~~~~~
                src/foo/test.kt:9: Warning: Error with receiver and arguments [Order]
                    bean.foo = s
                    ~~~~~~~~~~~~
                src/foo/test.kt:9: Warning: Error with receiver and no arguments [Order]
                    bean.foo = s
                    ~~~~~~~~
                0 errors, 18 warnings
                """
            )
    }

    class TestAnnotationLookupDetector : Detector(), SourceCodeScanner {
        companion object Issues {
            val ISSUE = Issue.create(
                "Order",
                "Sample test detector summary",
                "Sample test detector explanation",

                Category.CORRECTNESS, 6, Severity.WARNING,
                Implementation(
                    TestAnnotationLookupDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
        }

        override fun getApplicableUastTypes(): List<Class<out UElement>> =
            listOf(UMethod::class.java, UVariable::class.java, UCallExpression::class.java)

        class AnnotationOrderVisitor(private val context: JavaContext) : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                processAnnotations(node)
            }

            override fun visitMethod(node: UMethod) {
                val x = node.asRecursiveLogString()
                processAnnotations(node)
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Also test location ranges for assignments here
                val methodName = node.methodName ?: node.methodIdentifier?.name
                if (methodName == "setFoo") {
                    context.report(ISSUE, node, context.getCallLocation(node, false, true), "Error with arguments but no receiver")
                    context.report(ISSUE, node, context.getCallLocation(node, true, true), "Error with receiver and arguments")
                    context.report(ISSUE, node, context.getCallLocation(node, true, false), "Error with receiver and no arguments")
                }
            }

            private fun processAnnotations(modifierListOwner: PsiModifierListOwner) {
                context.evaluator.findAnnotationInHierarchy(modifierListOwner, "org.foo.bar")
                context.evaluator.findAnnotation(modifierListOwner, "org.foo.bar")
                context.evaluator.getAllAnnotations(
                    modifierListOwner,
                    true
                ).mapNotNull { it.qualifiedName?.split(".")?.lastOrNull() }
                // This detector doesn't actually report anything; the regression test
                // ensures that the above calls don't crash
            }
        }

        override fun createUastHandler(context: JavaContext): UElementHandler? {
            return AnnotationOrderVisitor(context)
        }
    }
}
