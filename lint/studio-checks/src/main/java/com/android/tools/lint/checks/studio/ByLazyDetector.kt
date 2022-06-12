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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.isInjectionHost
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Kotlin's "by lazy" looks like an easy replacement for lazy
 * initialization in Java, but it's actually much more involved than it
 * looks. This detector flags some cases where we should avoid it.
 */
class ByLazyDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            ByLazyDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AvoidByLazy",
            briefDescription = "Avoid using `by lazy` for simple lazy initialization",
            explanation = """
                Kotlin's `by lazy` feature looks like an easy and convenient replacement \
                for code ported from Java where a field was lazily initialized through a \
                getter. However, it's much more involved than it looks; try the \
                "Show Kotlin Bytecode" feature to see what it does, creating a new property \
                object etc. There are other patterns you can typically use.

                A common scenario for this happens when you port code from Java to Kotlin; \
                the Java code has a nullable field, which you null check and initialize before \
                using. If you just directly port this code to Kotlin, you're forced to make the \
                field nullable, and then all your accesses have to use `?.` or `!!` to avoid \
                warnings, so it's tempting to make it non-null with `by lazy` initialization.

                However, there's another way to handle this, also used widely in our codebase: \
                creating a local shadow, non-nullable, which is initialized lazily.

                Let's say for example you have a field named `foo` of type `Foo?`. To access \
                this safely, before your block using the field, add the following declaration:

                ```kotlin
                val foo = foo ?: Foo().also { foo = it }
                ```

                In other words, we're creating a local variable named foo, initialized from the \
                (nullable) field foo, but if it's null, also initialize it (calling `Foo()` as well \
                as storing it into the nullable field `foo` for future access.

                (If the field is referenced in many places, you may want to wrap it in a property \
                which uses a second private field, perhaps named with a `_` prefix, for lazy \
                initialization.)

                Note that this lint check just tries to identify naive usages of `by lazy` where \
                the lazy computation only seems to initialize the object and it's likely that it was \
                a simple attempt to preserve lazy initialization from Java. If you are really \
                deliberately using the language construct, be sure to specify a threading mode \
                as a parameter to the `lazy` call; in that case, this lint check will ignore it.
                ```
            """,
            category = CORRECTNESS,
            severity = ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("lazy")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(node.resolve(), "kotlin.LazyKt__LazyJVMKt")) {
            return
        }

        if (context.isTestSource) {
            return
        }

        // If you specify an explicit threading mode, the first argument will be the mode instead;
        // we don't want to flag these since they appear to be more deliberate lazy usages.
        val lambda = node.valueArguments.firstOrNull() as? ULambdaExpression ?: return
        if (onlyInitializes(lambda)) {
            val location = context.getLocation(node.methodIdentifier)
            context.report(ISSUE, node, location, "Avoid `by lazy` for simple lazy initialization")
        }
    }

    private fun onlyInitializes(lambda: ULambdaExpression): Boolean {
        val body = lambda.body
        if (body is UBlockExpression) {
            val expressions = body.expressions
            if (expressions.size == 1) {
                val expression = expressions[0]
                if (expression is UReturnExpression) {
                    val returnExpression = expression.returnExpression?.skipParenthesizedExprDown() ?: return false
                    val selector = returnExpression.findSelector()
                    return if (selector is UCallExpression) {
                        val name = selector.methodName ?: selector.methodIdentifier?.name
                        !(name == "apply" || name == "let" || name == "also")
                    } else {
                        selector.isInjectionHost()
                    }
                }
            }
        }
        return false
    }
}
