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

package com.android.tools.lint.detector.api

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.use
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LintDocExample")
class UImplicitCallExpressionTest {
    private fun lint(): TestLintTask {
        return TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())
    }

    @Test
    fun testWrapping() {
        listOf(
            kotlin(
                """
                package test.pkg

                class Resource {
                    operator fun contains(id: Int): Boolean = false
                    operator fun times(id: Int): Int = 0
                    operator fun rangeTo(id: Int): Int = 0
                    operator fun get(index1: Int, index2: Int, index3: Int): String = ""
                    operator fun set(index1: Int, index2: Int, index3: Int, value: String) { }
                    infix fun combine(other: Resource) { }
                }
                operator fun Resource.div(x: Int): Int = 0

                fun testBinary(resource: Resource, resource2: Resource, color: Int, string: Int) {
                    println(resource[1, 2, 3])
                    resource[1, 2, 3] = "hello"
                    println(color in resource)
                    println(string !in resource)
                    println(resource * string)
                    println(resource..string)
                    println(resource.combine(resource2))
                    println(resource combine resource2)
                    println(resource / color)
                }
                """
            ).indented()
        ).use { context ->
            val file = context.uastFile!!

            val sb = StringBuilder()
            file.accept(object : UastCallVisitor() {
                override fun visitCall(node: UCallExpression): Boolean {
                    assertEquals(node.lang, KotlinLanguage.INSTANCE)
                    val name = node.methodName
                    if (name == "println") {
                        return false
                    }
                    val receiver = node.receiver
                    if (receiver != null) {
                        sb.append("${receiver.sourcePsi?.text ?: ""}.")
                    }
                    sb.append("$name(${node.valueArguments.joinToString(",") { it.sourcePsi?.text ?: "" }})\n")
                    if (node is UImplicitCallExpression) {
                        sb.append("  from \"${node.sourcePsi?.text}\"\n")
                    }
                    return false
                }
            })

            assertEquals(
                """
                resource.get(1,2,3)
                  from "resource[1, 2, 3]"
                resource.set(1,2,3,hello)
                  from "resource[1, 2, 3] = "hello""
                resource.contains(color)
                  from "color in resource"
                resource.contains(string)
                  from "string !in resource"
                resource.times(string)
                  from "resource * string"
                resource.rangeTo(string)
                  from "resource..string"
                resource.combine(resource2)
                resource.combine(resource2)
                  from "resource combine resource2"
                div(resource,color)
                  from "resource / color"
                """.trimIndent().trim(),
                sb.toString().trim()
            )
        }
    }

    @Test
    fun testArrayResolve() {
        listOf(
            kotlin(
                """
                package test.pkg

                class Test {
                    operator fun get(key: Int) {}
                    operator fun get(key: String) {}
                    operator fun get(key: Int, key2: Int) {}
                    operator fun get(key: Int, key2: A) {}
                    operator fun get(key: Int, key2: List<CharSequence>) {}
                }
                open class A

                fun testArrays(test: Test, string: Int, color: Int) {
                    val sb = listOf<StringBuilder>()
                    test[string, sb]
                }
                """
            ).indented()
        ).use { context ->

            var found = false
            context.uastFile?.accept(object : AbstractUastVisitor() {
                override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
                    found = true
                    assertEquals("test[string, sb]", node.sourcePsi?.text)
                    val resolved = node.resolveOperator()
                    assertEquals("operator fun get(key: Int, key2: List<CharSequence>) {}", resolved?.text)
                    return super.visitArrayAccessExpression(node)
                }
            })
            assertTrue(found)
        }
    }

    @Test
    fun testElementVisitor() {
        // Make sure that the call DSL support also works for non-AST calls. Here we have a custom
        // detector which just lists operator names that it cares about and reports any that are
        // called -- this lets us make sure that the element visitor is really invoking the call
        // mechanism for things like array access and binary operators.
        lint().files(
            kotlin(
                """
                package test.pkg

                import kotlin.random.Random

                class Test {
                    operator fun get(key: Int) {}
                    operator fun set(key: Int, value: Int) {}
                }

                fun testArrays(test: Test, string: Int, color: Int) {
                    test[string]
                    test[string] = color
                }

                data class Point(val x: Int, val y: Int) {
                    operator fun unaryMinus() = Point(-x, -y)
                    operator fun inc() = Point(x + 1, y + 1)
                }

                @CheckResult
                operator fun Point.unaryPlus() = Point(+x, +y)

                fun testUnary() {
                    var point = Point(10, 20)
                    -point
                    point++
                }

                data class Counter(val dayIndex: Int) {
                    @CheckResult
                    operator fun plus(increment: Int): Counter {
                        return Counter(dayIndex + increment)
                    }

                    @CheckResult
                    operator fun plus(other: Counter): Counter {
                        return Counter(dayIndex + other.dayIndex)
                    }
                }

                fun testBinary() {
                    val counter = Counter(5)
                    val x = counter + 5
                }

                class Resource {
                    operator fun contains(id: Int): Boolean = false
                    operator fun times(id: Int): Int = 0
                    operator fun rangeTo(id: Int): Int = 0
                }

                fun testBinary(resource: Resource, color: Int, string: Int) {
                    println(color in resource)
                    println(string !in resource)
                    println(resource * string) // not included in applicable method names, so should not be reported
                    println(resource..string)
                }
                """
            ).indented()
        ).issues(TestDispatchDetector.ISSUE).run().expect(
            """
            src/test/pkg/Test.kt:11: Error: Found overloaded function call get [_DispatchTestIssue]
                test[string]
                    ~
            src/test/pkg/Test.kt:12: Error: Found overloaded function call set [_DispatchTestIssue]
                test[string] = color
                             ~
            src/test/pkg/Test.kt:26: Error: Found overloaded function call inc [_DispatchTestIssue]
                point++
                     ~~
            src/test/pkg/Test.kt:43: Error: Found overloaded function call plus [_DispatchTestIssue]
                val x = counter + 5
                                ~
            src/test/pkg/Test.kt:53: Error: Found overloaded function call contains [_DispatchTestIssue]
                println(color in resource)
                              ~~
            src/test/pkg/Test.kt:54: Error: Found overloaded function call contains [_DispatchTestIssue]
                println(string !in resource)
                               ~~~
            src/test/pkg/Test.kt:56: Error: Found overloaded function call rangeTo [_DispatchTestIssue]
                println(resource..string)
                                ~~
            7 errors, 0 warnings
            """
        )
    }

    class TestDispatchDetector : Detector(), SourceCodeScanner {
        override fun getApplicableMethodNames(): List<String> =
            listOf("get", "set", "compareTo", "inc", "in", "rangeTo", "contains", "plus", "minus")

        override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
            context.report(
                ISSUE, context.getCallLocation(node, includeReceiver = false, includeArguments = false),
                "Found overloaded function call ${node.methodName}"
            )
        }

        companion object {
            val ISSUE = Issue.create(
                "_DispatchTestIssue", "Blah blah", "Blah blah blah",
                Category.CORRECTNESS, 5, Severity.ERROR,
                Implementation(TestDispatchDetector::class.java, Scope.JAVA_FILE_SCOPE)
            )
        }
    }
}
