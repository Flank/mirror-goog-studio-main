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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Detector.UastScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.visitor.UastVisitor

/**
 * The [UElementHandler] is similar to a [UastVisitor],
 * but it is used to only visit a single element. Detectors tell lint which types of elements
 * they want to be called for by invoking [UastScanner.getApplicableUastTypes].
 *
 * If you want to actually perform a full file visitor iteration you should implement the
 * link [.visitFile] and then create a [UastVisitor] and then invoke
 * that on `file.accept(visitor)`.
 */
open class UElementHandler {

    internal open fun error(parameterType: Class<out UElement>) {
        val name = parameterType.simpleName
        require(name.startsWith("U")) { name }
        throw RuntimeException(
            "You must override visit${name.substring(1)} " +
                "(and don't call super.visit${name.substring(1)}!)"
        )
    }

    open fun visitAnnotation(node: UAnnotation) {
        error(UAnnotation::class.java)
    }

    open fun visitArrayAccessExpression(node: UArrayAccessExpression) {
        error(UArrayAccessExpression::class.java)
    }

    open fun visitBinaryExpression(node: UBinaryExpression) {
        error(UBinaryExpression::class.java)
    }

    open fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
        error(UBinaryExpressionWithType::class.java)
    }

    open fun visitBlockExpression(node: UBlockExpression) {
        error(UBlockExpression::class.java)
    }

    open fun visitBreakExpression(node: UBreakExpression) {
        error(UBreakExpression::class.java)
    }

    open fun visitCallExpression(node: UCallExpression) {
        error(UCallExpression::class.java)
    }

    open fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        error(UCallableReferenceExpression::class.java)
    }

    open fun visitCatchClause(node: UCatchClause) {
        error(UCatchClause::class.java)
    }

    open fun visitClass(node: UClass) {
        error(UClass::class.java)
    }

    open fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        error(UClassLiteralExpression::class.java)
    }

    open fun visitContinueExpression(node: UContinueExpression) {
        error(UContinueExpression::class.java)
    }

    open fun visitDeclaration(node: UDeclaration) {
        error(UDeclaration::class.java)
    }

    open fun visitDeclarationsExpression(node: UDeclarationsExpression) {
        error(UDeclarationsExpression::class.java)
    }

    open fun visitDoWhileExpression(node: UDoWhileExpression) {
        error(UDoWhileExpression::class.java)
    }

    open fun visitElement(node: UElement) {
        error(UElement::class.java)
    }

    open fun visitEnumConstant(node: UEnumConstant) {
        error(UEnumConstant::class.java)
    }

    open fun visitExpression(node: UExpression) {
        error(UExpression::class.java)
    }

    open fun visitExpressionList(node: UExpressionList) {
        error(UExpressionList::class.java)
    }

    open fun visitField(node: UField) {
        error(UField::class.java)
    }

    open fun visitFile(node: UFile) {
        error(UFile::class.java)
    }

    open fun visitForEachExpression(node: UForEachExpression) {
        error(UForEachExpression::class.java)
    }

    open fun visitForExpression(node: UForExpression) {
        error(UForExpression::class.java)
    }

    open fun visitIfExpression(node: UIfExpression) {
        error(UIfExpression::class.java)
    }

    open fun visitImportStatement(node: UImportStatement) {
        error(UImportStatement::class.java)
    }

    open fun visitInitializer(node: UClassInitializer) {
        error(UClassInitializer::class.java)
    }

    open fun visitLabeledExpression(node: ULabeledExpression) {
        error(ULabeledExpression::class.java)
    }

    open fun visitLambdaExpression(node: ULambdaExpression) {
        error(ULambdaExpression::class.java)
    }

    open fun visitLiteralExpression(node: ULiteralExpression) {
        error(ULiteralExpression::class.java)
    }

    open fun visitLocalVariable(node: ULocalVariable) {
        error(ULocalVariable::class.java)
    }

    open fun visitMethod(node: UMethod) {
        error(UMethod::class.java)
    }

    open fun visitObjectLiteralExpression(node: UObjectLiteralExpression) {
        error(UObjectLiteralExpression::class.java)
    }

    open fun visitParameter(node: UParameter) {
        error(UParameter::class.java)
    }

    open fun visitParenthesizedExpression(node: UParenthesizedExpression) {
        error(UParenthesizedExpression::class.java)
    }

    open fun visitPolyadicExpression(node: UPolyadicExpression) {
        error(UPolyadicExpression::class.java)
    }

    open fun visitPostfixExpression(node: UPostfixExpression) {
        error(UPostfixExpression::class.java)
    }

    open fun visitPrefixExpression(node: UPrefixExpression) {
        error(UPrefixExpression::class.java)
    }

    open fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
        error(UQualifiedReferenceExpression::class.java)
    }

    open fun visitReturnExpression(node: UReturnExpression) {
        error(UReturnExpression::class.java)
    }

    open fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        error(USimpleNameReferenceExpression::class.java)
    }

    open fun visitSuperExpression(node: USuperExpression) {
        error(USuperExpression::class.java)
    }

    open fun visitSwitchClauseExpression(node: USwitchClauseExpression) {
        error(USwitchClauseExpression::class.java)
    }

    open fun visitSwitchExpression(node: USwitchExpression) {
        error(USwitchExpression::class.java)
    }

    open fun visitThisExpression(node: UThisExpression) {
        error(UThisExpression::class.java)
    }

    open fun visitThrowExpression(node: UThrowExpression) {
        error(UThrowExpression::class.java)
    }

    open fun visitTryExpression(node: UTryExpression) {
        error(UTryExpression::class.java)
    }

    open fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        error(UTypeReferenceExpression::class.java)
    }

    open fun visitUnaryExpression(node: UUnaryExpression) {
        error(UUnaryExpression::class.java)
    }

    open fun visitVariable(node: UVariable) {
        error(UVariable::class.java)
    }

    open fun visitWhileExpression(node: UWhileExpression) {
        error(UWhileExpression::class.java)
    }

    open fun visitYieldExpression(node: UYieldExpression) {
        error(UYieldExpression::class.java)
    }

    companion object {
        val NONE: UElementHandler = object : UElementHandler() {
            override fun error(parameterType: Class<out UElement>) {}
        }
    }
}
