/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Warns about using the wrong canvas size in custom views
 */
class CanvasSizeDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(CLASS_VIEW, CLASS_DRAWABLE)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        val drawMethods = declaration.methods.filter {
            it.name == ON_DRAW || it.name == DRAW && evaluator.parametersMatch(it, CLASS_CANVAS)
        }
        for (method in drawMethods) {
            method.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val name = getMethodName(node)
                    if (name == GET_WIDTH || name == GET_HEIGHT) {
                        val sizeMethod = node.resolve()
                        if (sizeMethod != null &&
                            context.evaluator.isMemberInClass(sizeMethod, CLASS_CANVAS)
                        ) {
                            reportWarning(context, node, name, declaration)
                        }
                    }

                    return super.visitCallExpression(node)
                }

                override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
                    // Look for Kotlin property-access of the canvas size methods
                    val selector = node.selector
                    if (selector is USimpleNameReferenceExpression) {
                        val name = selector.identifier
                        if (name == WIDTH || name == HEIGHT) {
                            val receiver = node.receiver
                            val type = receiver.getExpressionType()
                            if (type?.canonicalText == CLASS_CANVAS) {
                                reportWarning(context, node, name, declaration)
                            }
                        }
                    }
                    return super.visitQualifiedReferenceExpression(node)
                }
            })
        }
    }

    private fun reportWarning(
        context: JavaContext,
        node: UElement,
        name: String,
        containingClass: UClass
    ) {
        val drawable = context.evaluator.extendsClass(containingClass, CLASS_DRAWABLE, false)
        val calling = node is UCallExpression
        val verb = if (calling) "Calling" else "Referencing"
        val container = if (drawable) "Drawable" else "Canvas"
        val replacement = if (drawable) "getBounds()" else name
        val message =
            "$verb $container.$name is usually wrong; you should be calling $replacement instead"

        val range = context.getLocation(node)

        val fix =
            fix()
                .name("${if (calling) "Call" else "Reference"} ${if (drawable) "getBounds" else name}${if (calling) "()" else ""} instead")
                .replace()
                .range(range)
                .pattern(if (drawable) "(.*)" else "(.*)$name")
                .with(if (drawable) "getBounds().${if (name == GET_WIDTH) WIDTH else if (name == GET_HEIGHT) HEIGHT else name}()" else "")
                .build()
        context.report(ISSUE, node, context.getLocation(node), message, fix)
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(CanvasSizeDetector::class.java, Scope.JAVA_FILE_SCOPE)

        /** Wrong canvas size lookup  */
        @JvmField
        val ISSUE = Issue.create(
            id = "CanvasSize",
            briefDescription = "Wrong Canvas Size",
            explanation = """
                In a custom view's draw implementation, you should normally call `getWidth` \
                and `getHeight` on the custom view itself, not on the `canvas` instance.

                Canvas width and height are the width and height of the `Canvas`, which is \
                not always the same as size of the view.

                In the hardware accelerated path the width and height of the canvas \
                typically always match that of the `View` because every view goes to its own \
                recorded `DisplayList`. But in software rendering there's just one canvas \
                that is clipped and transformed as it makes its way through the `View` tree, \
                and otherwise remains the same `Canvas` object for every View's draw method.

                You should only use Canvas state to adjust how much you draw, such as a \
                quickReject for early work avoidance if it's going to be clipped away, but \
                not what you draw.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val CLASS_CANVAS = "android.graphics.Canvas"
        private const val CLASS_DRAWABLE = "android.graphics.drawable.Drawable"
        private const val ON_DRAW = "onDraw"
        private const val DRAW = "draw"
        private const val GET_WIDTH = "getWidth"
        private const val GET_HEIGHT = "getHeight"
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
    }
}
