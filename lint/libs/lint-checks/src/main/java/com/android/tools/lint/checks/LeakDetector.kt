/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.CLASS_APPLICATION
import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Locale

/** Looks for leaks via static fields */
class LeakDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return SUPER_CLASSES
    }

    /** Warn about inner classes that aren't static: these end up retaining the outer class */
    override fun visitClass(context: JavaContext, declaration: UClass) {
        val containingClass = declaration.getContainingUClass()
        val isAnonymous = declaration is UAnonymousClass

        // Only consider static inner classes
        val evaluator = context.evaluator
        val isStatic = evaluator.isStatic(declaration) || containingClass == null
        if (isStatic || isAnonymous) { // containingClass == null: implicitly static
            // But look for fields that store contexts
            for (field in declaration.fields) {
                checkInstanceField(context, field)
            }

            if (!isAnonymous) {
                return
            }
        }

        var superClass: String? = null
        for (cls in SUPER_CLASSES) {
            if (evaluator.inheritsFrom(declaration, cls, false)) {
                superClass = cls
                break
            }
        }
        superClass ?: return

        val uastParent = declaration.uastParent
        if (uastParent != null) {

            val method = uastParent.getParentOfType<UMethod>(
                UMethod::class.java,
                true,
                UClass::class.java,
                UObjectLiteralExpression::class.java
            )
            if (method != null && evaluator.isStatic(method)) {
                return
            }
        }

        val invocation = declaration.getParentOfType<UCallExpression>(
            UObjectLiteralExpression::class.java,
            true,
            UMethod::class.java
        )

        val location: Location
        location = if (isAnonymous && invocation != null) {
            context.getCallLocation(invocation, false, false)
        } else {
            context.getNameLocation(declaration)
        }
        var name: String?
        if (isAnonymous) {
            name = "anonymous " + (declaration as UAnonymousClass)
                .baseClassReference
                .qualifiedName
        } else {
            name = declaration.qualifiedName
            if (name == null) {
                name = declaration.name
            }
        }

        val superClassName = superClass.substring(superClass.lastIndexOf('.') + 1)
        context.report(
            ISSUE,
            declaration,
            location,
            "This `$superClassName` class should be static or leaks might occur ($name)"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf<Class<out UElement>>(UField::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return FieldChecker(context)
    }

    private class FieldChecker(private val context: JavaContext) : UElementHandler() {

        override fun visitField(node: UField) {
            val modifierList = node.modifierList
            if (modifierList == null || !modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                return
            }

            val type = node.type as? PsiClassType ?: return
            val fqn = type.canonicalText
            if (fqn.startsWith("java.")) {
                return
            }
            val cls = type.resolve() ?: return
            if (fqn.startsWith("android.")) {
                if (isLeakCandidate(cls, context.evaluator) &&
                    !isAppContextName(cls, node) &&
                    !isInitializedToAppContext(node, cls)
                ) {
                    val message =
                        "Do not place Android context classes in static fields; " +
                                "this is a memory leak"
                    report(node, modifierList, message)
                }
            } else {
                // User application object -- look to see if that one itself has
                // static fields?
                // We only check *one* level of indirection here
                for ((count, referenced) in cls.allFields.withIndex()) {
                    // Only check a few; avoid getting bogged down on large classes
                    if (count == 20) {
                        break
                    }

                    val innerType = referenced.type as? PsiClassType ?: continue

                    val canonical = innerType.canonicalText
                    if (canonical.startsWith("java.")) {
                        continue
                    }
                    val innerCls = innerType.resolve() ?: continue

                    if (canonical.startsWith("android.")) {
                        if (isLeakCandidate(innerCls, context.evaluator) &&
                            !isAppContextName(innerCls, referenced) &&
                            !isInitializedToAppContext(context, referenced, innerCls)
                        ) {
                            val message = "Do not place Android context classes in static " +
                                    "fields (static reference to `${cls.name}` which has field " +
                                    "`${referenced.name}` pointing to `${innerCls.name}`); this " +
                                    "is a memory leak"
                            report(node, modifierList, message)
                            break
                        }
                    }
                }
            }
        }

        private fun isInitializedToAppContext(
            context: JavaContext,
            field: PsiField,
            typeClass: PsiClass
        ): Boolean {
            if (!context.evaluator.extendsClass(typeClass, CLASS_CONTEXT, false)) {
                return false
            }

            val uField = field.toUElement(UField::class.java) ?: return true
            return isInitializedToAppContext(uField, typeClass)
        }

        /**
         * If it's a static field see if it's initialized to an app context in one of the
         * constructors
         */
        private fun isInitializedToAppContext(field: UField, typeClass: PsiClass): Boolean {
            val containingClass = field.containingClass ?: return false

            // Only check for app context if we're dealing with a Context field -- there's
            // no chance Fragments, Views etc will be the app context.
            if (!context.evaluator.extendsClass(typeClass, CLASS_CONTEXT, false)) {
                return false
            }

            for (method in containingClass.constructors) {
                val methodBody = context.uastContext.getMethodBody(method) ?: continue
                val assignedToAppContext = Ref(false)

                methodBody.accept(
                    object : AbstractUastVisitor() {
                        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                            if (node.isAssignment() &&
                                node.leftOperand is UResolvable &&
                                field.sourcePsi == (node.leftOperand as UResolvable)
                                    .resolve()
                            ) {
                                // Yes, assigning to this field
                                // See if the right hand side looks like an app context
                                var rhs: UElement = node.rightOperand
                                while (rhs is UQualifiedReferenceExpression) {
                                    rhs = rhs.selector
                                }
                                if (rhs is UCallExpression) {
                                    if ("getApplicationContext" == getMethodName(rhs)) {
                                        assignedToAppContext.set(true)
                                    }
                                }
                            }
                            return super.visitBinaryExpression(node)
                        }
                    })

                if (assignedToAppContext.get()) {
                    return true
                }
            }

            return false
        }

        private fun report(
            field: PsiField,
            modifierList: PsiModifierList,
            message: String
        ) {
            var locationNode: PsiElement = field
            // Try to find the static modifier itself
            if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                var child: PsiElement? = modifierList.firstChild
                while (child != null) {
                    if (child is PsiKeyword && PsiKeyword.STATIC == child.text) {
                        locationNode = child
                        break
                    }
                    child = child.nextSibling
                }
            }
            val location = context.getLocation(locationNode)
            context.report(ISSUE, field, location, message)
        }
    }

    private fun checkInstanceField(context: JavaContext, field: UField) {
        val type = field.type as? PsiClassType ?: return
        val fqn = type.canonicalText
        if (fqn.startsWith("java.")) {
            return
        }
        val cls = type.resolve() ?: return

        if (isLeakCandidate(cls, context.evaluator)) {
            context.report(
                LeakDetector.ISSUE,
                field,
                context.getLocation(field),
                "This field leaks a context object"
            )
        }
    }

    companion object {
        /** Leaking data via static fields */
        @JvmField
        val ISSUE = Issue.create(
            id = "StaticFieldLeak",
            briefDescription = "Static Field Leaks",
            explanation = """
                A static field will leak contexts.

                Non-static inner classes have an implicit reference to their outer class. \
                If that outer class is for example a `Fragment` or `Activity`, then this \
                reference means that the long-running handler/loader/task will hold a \
                reference to the activity which prevents it from getting garbage collected.

                Similarly, direct field references to activities and fragments from these \
                longer running instances can cause leaks.

                ViewModel classes should never point to Views or non-application Contexts.
                """,
            category = Category.PERFORMANCE,
            androidSpecific = true,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(LeakDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        private val SUPER_CLASSES = listOf(
            "android.content.Loader",
            "android.support.v4.content.Loader",
            "android.os.AsyncTask",
            "android.arch.lifecycle.ViewModel"
        )

        private fun isAppContextName(cls: PsiClass, field: PsiField): Boolean {
            // Don't flag names like "sAppContext" or "applicationContext".
            val name = field.name
            val lower = name.toLowerCase(Locale.US)
            if (lower.contains("appcontext") || lower.contains("application")) {
                if (CLASS_CONTEXT == cls.qualifiedName) {
                    return true
                }
            }

            return false
        }

        private fun isLeakCandidate(cls: PsiClass, evaluator: JavaEvaluator): Boolean {
            return (evaluator.extendsClass(
                cls,
                CLASS_CONTEXT,
                false
            ) && !evaluator.extendsClass(cls, CLASS_APPLICATION, false) ||
                    evaluator.extendsClass(cls, CLASS_VIEW, false) ||
                    evaluator.extendsClass(cls, CLASS_FRAGMENT, false))
        }
    }
}
