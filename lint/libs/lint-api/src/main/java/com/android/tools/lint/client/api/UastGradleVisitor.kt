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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.getMethodName
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Gradle visitor for Kotlin Script files */
class UastGradleVisitor(private val javaContext: JavaContext) : GradleVisitor() {
    override fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
        val uastFile = javaContext.uastFile ?: return
        uastFile.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    handleMethodCall(node, detectors, context)
                    return super.visitCallExpression(node)
                }

                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    handleBinaryExpression(node, detectors, context)
                    return super.visitBinaryExpression(node)
                }

                override fun visitReturnExpression(node: UReturnExpression): Boolean {
                    val returnExpression = node.returnExpression
                    if (returnExpression is UCallExpression) {
                        visitCallExpression(returnExpression)
                    }
                    return super.visitReturnExpression(node)
                }
            })
    }

    private fun handleBinaryExpression(
        node: UBinaryExpression,
        detectors: List<GradleScanner>,
        context: GradleContext
    ) {
        if (node.isAssignment()) {
            val parentCall = getSurroundingNamedBlock(node)
            if (parentCall != null) {
                val parentParentCall = getSurroundingNamedBlock(parentCall)
                val parentName =
                    getMethodName(parentCall)
                val parentParentName = if (parentParentCall != null)
                    getMethodName(parentParentCall)
                else null
                if (parentName != null) {
                    val target = node.leftOperand.asSourceString()
                    val value = node.rightOperand.asSourceString()
                    for (scanner in detectors) {
                        scanner.checkDslPropertyAssignment(
                            context,
                            target,
                            value,
                            parentName,
                            parentParentName,
                            node.leftOperand,
                            node.rightOperand,
                            node
                        )
                    }
                }
            }
        } else {
            val left = node.leftOperand
            if (left is UCallExpression) {
                // Constructs like
                //    plugins {
                //        id("com.android.application") version "2.3.3"
                // (corresponds to Gradle: apply plugin: 'com.android.application'
                // and classpath 'com.android.tools.build:gradle:2.3.3')
                //
                // with UAST
                //
                //    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                //        UIdentifier (Identifier (plugins))
                //        USimpleNameReferenceExpression (identifier = <anonymous class>)
                //        ULambdaExpression
                //            UBlockExpression
                //   ===>         UBinaryExpression (operator = <other>)
                //                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1))
                //                        UIdentifier (Identifier (id))
                //                        USimpleNameReferenceExpression (identifier = <anonymous class>)
                //                        ULiteralExpression (value = "com.android.application")
                //                    ULiteralExpression (value = "2.3.3")

                val valueArguments = left.valueArguments
                if (valueArguments.size == 1) {
                    val target = getMethodName(left)
                        ?: ""
                    val arg = valueArguments[0]
                    if (arg !is ULambdaExpression) {
                        // Some sort of DSL property?
                        // Parent should be block, its parent lambda, its parent a call -
                        // the name is the parent
                        val parentCall = getSurroundingNamedBlock(node)
                        if (parentCall != null) {
                            val parentParentCall =
                                getSurroundingNamedBlock(parentCall)
                            val parentName =
                                getMethodName(
                                    parentCall
                                )
                            val parentParentName = if (parentParentCall != null)
                                getMethodName(
                                    parentParentCall
                                )
                            else null
                            if (parentName != null) {
                                val value = arg.asSourceString()
                                for (scanner in detectors) {
                                    // How do I represent this: we're passing
                                    // in *two* values:
                                    //  the plugin id
                                    // and then the version to use
                                    // For now just passing the first one, e.g.
                                    // in the above, plugins.id = "com.android.application"

                                    scanner.checkDslPropertyAssignment(
                                        context,
                                        target,
                                        value,
                                        parentName,
                                        parentParentName,
                                        left,
                                        arg,
                                        node
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleMethodCall(
        node: UCallExpression,
        detectors: List<GradleScanner>,
        context: GradleContext
    ) {
        val valueArguments = node.valueArguments
        val propertyName = getMethodName(node)
        if (propertyName != null && valueArguments.size == 1) {
            val arg = valueArguments[0]
            if (arg !is ULambdaExpression) {
                // Some sort of DSL property?
                // Parent should be block, its parent lambda, its parent a call -
                // the name is the parent
                val parentCall = getSurroundingNamedBlock(node)
                if (parentCall != null) {
                    val parentParentCall = getSurroundingNamedBlock(parentCall)
                    val parentName =
                        getMethodName(parentCall)
                    val parentParentName = if (parentParentCall != null)
                        getMethodName(
                            parentParentCall
                        )
                    else null
                    if (parentName != null) {
                        val value = arg.asSourceString()
                        for (scanner in detectors) {
                            scanner.checkDslPropertyAssignment(
                                context,
                                propertyName,
                                value,
                                parentName,
                                parentParentName,
                                node.methodIdentifier ?: node,
                                arg,
                                node
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getSurroundingNamedBlock(node: UElement): UCallExpression? {
        var parent = node.uastParent
        if (parent is UReturnExpression) {
            // parent may be a UReturnExpression child of UBlockExpression
            parent = parent?.uastParent
        }
        if (parent is UBlockExpression) {
            val parentParent = parent.uastParent
            if (parentParent is ULambdaExpression) {
                val parentCall = parentParent.uastParent
                if (parentCall is UCallExpression) {
                    return parentCall
                }
            }
        }

        return null
    }

    override fun createLocation(context: GradleContext, cookie: Any): Location {
        return javaContext.getLocation(cookie as UElement)
    }

    override fun getStartOffset(context: GradleContext, cookie: Any): Int {
        val start = javaContext.getLocation(cookie as UElement).start
        return start?.offset ?: -1
    }
}
