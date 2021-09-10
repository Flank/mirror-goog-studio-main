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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class PathAsIterableDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            PathAsIterableDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PathAsIterable",
            briefDescription = "Accidentally Using Path as Iterable",
            explanation = """
                `Path` implements `Iterable`, so some methods will process a path by \
                iterating through each path segment. This is sometimes not what you want \
                or expect, and has led to serious bugs in the past, such as b/196713590 \
                where an attempt to place a path into a list (using the `Lists.newArrayList` \
                constructor method which has an iterable form, which proceeded to add each \
                path instead of a list with a single element in it).
                """,
            category = CORRECTNESS,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                for (argument in node.valueArguments) {
                    val type = argument.getExpressionType() ?: continue
                    if (type.canonicalText == "java.nio.file.Path") {
                        val method = node.resolve() ?: continue
                        val argumentMapping = context.evaluator.computeArgumentMapping(node, method)
                        val parameter = argumentMapping[argument] ?: continue
                        val parameterType = parameter.type
                        if (parameterType.canonicalText.startsWith("java.lang.Iterable<")) {
                            context.report(
                                ISSUE, argument, context.getLocation(argument),
                                "Using `Path` in an `Iterable` context: make sure this is doing what you expect and suppress this warning if so"
                            )
                        }
                    }
                }
            }
        }
    }
}
