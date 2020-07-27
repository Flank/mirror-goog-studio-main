/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detect incorrect construction of JEditorPanes with text/html content
 * type.
 */
class HtmlPaneDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            HtmlPaneDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "HtmlPaneColors",
            briefDescription = "Incorrect HTML JEditorPane",
            explanation =
                """
                If you construct `JEditorPane` and just set the content type
                to `text/html` (either into the constructor or via an explicit setter),
                the UI may not use correct colors in all themes. Instead you should
                make sure you use the HTML Editor kit, or better yet, directly
                use `SwingHelper#createHtmlViewer`.
                """,
            category = CORRECTNESS,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION,
            moreInfo = "https://issuetracker.google.com/157600808"
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf("javax.swing.JEditorPane", "javax.swing.JTextPane")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val arguments = node.valueArguments
        if (arguments.size == 2) {
            val contentTypeParameter = arguments[1]
            checkContentTypeWithoutEditorKit(context, contentTypeParameter, node)
        }
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("setContentType")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val arguments = node.valueArguments
        if (arguments.size == 1) {
            checkContentTypeWithoutEditorKit(context, arguments[0], node)
        }
    }

    private fun checkContentTypeWithoutEditorKit(
        context: JavaContext,
        contentTypeParameter: UExpression,
        locationElement: UElement
    ) {
        val contentType = ConstantEvaluator.evaluate(context, contentTypeParameter)
        if (contentType == "text/html" && !setsEditorKit(contentTypeParameter)) {
            context.report(
                ISSUE, contentTypeParameter, context.getLocation(locationElement),
                "Constructing an HTML JEditorPane directly can lead to subtle theming " +
                    "bugs; either set the editor kit directly " +
                    "(`setEditorKit(UIUtil.getHTMLEditorKit())`) or better yet use " +
                    "`SwingHelper.createHtmlViewer`"
            )
        }
    }

    private fun setsEditorKit(element: UExpression): Boolean {
        val method = element.getParentOfType<UMethod>(UMethod::class.java, true)
            ?: return false
        val finder = SetEditorKitFinder()
        method.accept(finder)
        return finder.found
    }

    private class SetEditorKitFinder : AbstractUastVisitor() {
        var found = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            // Call syntax
            if (node.methodName == "setEditorKit") {
                found = true
            }
            return if (found) {
                true
            } else {
                super.visitCallExpression(node)
            }
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (!node.isAssignment()) {
                return super.visitBinaryExpression(node)
            }

            // Property syntax
            val left = node.leftOperand
            if (left is UReferenceExpression && left.resolvedName == "editorKit") {
                found = true
            }

            return if (found) {
                true
            } else {
                return super.visitBinaryExpression(node)
            }
        }
    }
}
