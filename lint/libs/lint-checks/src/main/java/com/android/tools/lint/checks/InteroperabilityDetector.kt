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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintUtils.isKotlin
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType

/**
 * Checks for issues around creating APIs that make it harder
 * to interoperate between Java and Kotlin code.
 *
 * See https://android.github.io/kotlin-guides/interop.html .
 */
class InteroperabilityDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            InteroperabilityDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val NO_HARD_KOTLIN_KEYWORDS = Issue.create(
            id = "NoHardKeywords",
            briefDescription = "No Hard Kotlin Keywords",

            explanation = """
            Do not use Kotlin’s hard keywords as the name of methods or fields.
            These require the use of backticks to escape when calling from Kotlin.
            Soft keywords, modifier keywords, and special identifiers are allowed.

            For example, Mockito’s `when` function requires backticks when used from Kotlin:

                val callable = Mockito.mock(Callable::class.java)
                Mockito.\`when\`(callable.call()).thenReturn(/* … */)
            """,
            moreInfo = "https://android.github.io/kotlin-guides/interop.html#no-hard-keywords",
            category = Category.INTEROPERABILITY_KOTLIN,
            priority = 6,
            severity = Severity.WARNING,
            enabledByDefault = false,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val LAMBDA_LAST = Issue.create(
            id = "LambdaLast",
            briefDescription = "Lambda Parameters Last",

            explanation = """
            To improve calling this code from Kotlin,
            parameter types eligible for SAM conversion should be last.
            """,
            moreInfo = "https://android.github.io/kotlin-guides/interop.html#lambda-parameters-last",
            category = Category.INTEROPERABILITY_KOTLIN,
            priority = 6,
            severity = Severity.WARNING,
            enabledByDefault = false,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val PLATFORM_NULLNESS = Issue.create(
            id = "UnknownNullness",
            briefDescription = "Unknown nullness",

            explanation = """
            To improve referencing this code from Kotlin, consider adding
            explicit nullness information here with either `@NonNull` or `@Nullable`.
            """,
            moreInfo = "https://android.github.io/kotlin-guides/interop.html#nullability-annotations",
            category = Category.INTEROPERABILITY_KOTLIN,
            priority = 6,
            severity = Severity.WARNING,
            enabledByDefault = false,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val KOTLIN_PROPERTY = Issue.create(
            id = "KotlinPropertyAccess",
            briefDescription = "Kotlin Property Access",

            explanation = """
            For a method to be represented as a property in Kotlin, strict “bean”-style prefixing must be used.

            Accessor methods require a ‘get’ prefix or for boolean-returning methods an ‘is’ prefix can be used.
            """,
            moreInfo = "https://android.github.io/kotlin-guides/interop.html#property-prefixes",
            category = Category.INTEROPERABILITY_KOTLIN,
            priority = 6,
            severity = Severity.WARNING,
            enabledByDefault = false,
            implementation = IMPLEMENTATION
        )

        private fun isKotlinHardKeyword(keyword: String): Boolean {
            // From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
            when (keyword) {
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while"
                -> return true
            }

            return false
        }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        // using deprecated psi field here instead of sourcePsi because the IDE
        // still uses older version of UAST
        if (isKotlin(context.uastFile?.psi)) {
            // These checks apply only to Java code
            return null
        }
        return JavaVisitor(context)
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java, UField::class.java)
    }

    class JavaVisitor(val context: JavaContext) : UElementHandler() {
        private val checkForKeywords = context.isEnabled(NO_HARD_KOTLIN_KEYWORDS)
        private val checkNullness = context.isEnabled(PLATFORM_NULLNESS)
        private val checkLambdaLast = context.isEnabled(LAMBDA_LAST)
        private val checkPropertyAccess = context.isEnabled(KOTLIN_PROPERTY)

        override fun visitMethod(node: UMethod) {
            if (isApi(node)) {
                val methodName = node.name

                if (checkForKeywords) {
                    ensureNonKeyword(methodName, node, "method")
                }

                if (checkPropertyAccess && isLikelySetter(methodName, node)) {
                    ensureValidProperty(node, methodName)
                }

                if (checkLambdaLast) {
                    ensureLambdaLastParameter(node)
                }

                if (checkNullness) {
                    val type = node.returnType
                    if (type != null) { // not a constructor
                        ensureNullnessKnown(node, type)
                    }
                    for (parameter in node.uastParameters) {
                        ensureNullnessKnown(parameter, parameter.type)
                    }
                }
            }
        }

        override fun visitField(node: UField) {
            if (isApi(node)) {
                if (checkForKeywords) {
                    ensureNonKeyword(node.name, node, "field")
                }
                if (checkNullness) {
                    ensureNullnessKnown(node, node.type)
                }
            }
        }

        private fun isLikelySetter(
            methodName: String,
            node: UMethod
        ): Boolean {
            return methodName.startsWith("set") && methodName.length > 3 &&
                    Character.isUpperCase(methodName[3]) &&
                    node.uastParameters.size == 1 &&
                    context.evaluator.isPublic(node) &&
                    !context.evaluator.isStatic(node)
        }

        private fun isApi(declaration: UDeclaration): Boolean {
            val evaluator = context.evaluator
            if (evaluator.isPublic(declaration) || evaluator.isProtected(declaration)) {
                val cls = declaration.getParentOfType<UClass>(UClass::class.java) ?: return true
                return evaluator.isPublic(cls) && cls !is UAnonymousClass
            }

            return false
        }

        private fun ensureValidProperty(setter: UMethod, methodName: String) {
            val cls = setter.getContainingUClass() ?: return
            val propertySuffix = methodName.substring(3)
            val propertyName = propertySuffix.decapitalize()
            val getterName1 = "get$propertySuffix"
            val getterName2 = "is$propertySuffix"
            val badGetterName = "has$propertySuffix"
            var getter: PsiMethod? = null
            var badGetter: UMethod? = null
            cls.methods.forEach {
                if (it.parameters.isEmpty()) {
                    when (it.name) {
                        getterName1, getterName2 -> getter = it
                        badGetterName, propertyName -> badGetter = it
                        else -> if (it.name.endsWith(propertySuffix) && badGetter == null) {
                            badGetter = it
                        }
                    }
                }
            }

            if (getter == null) {
                // Look for inherited methods
                cls.superClass?.let { superClass ->
                    for (inherited in superClass.findMethodsByName(getterName1, true)) {
                        if (inherited.parameterList.parametersCount == 0) {
                            getter = inherited
                            break
                        }
                    }
                    if (getter == null) {
                        for (inherited in superClass.findMethodsByName(getterName2, true)) {
                            if (inherited.parameterList.parametersCount == 0) {
                                getter = inherited
                                break
                            }
                        }
                    }
                }
            }

            if (getter != null) {
                @Suppress("NAME_SHADOWING") // compiler gets confused about getter nullness
                val getter = getter
                // enforce public and not static
                if (!context.evaluator.isPublic(getter)) {
                    val message = "This getter should be public such that `$propertyName` can " +
                            "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    val location = context.getNameLocation(getter!!)
                    context.report(KOTLIN_PROPERTY, setter, location, message)
                    return
                }

                if (context.evaluator.isStatic(getter)) {
                    var staticElement: PsiElement? = null
                    val modifierList = getter!!.modifierList
                    // Try to find the static modifier itself
                    if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                        var child: PsiElement? = modifierList.firstChild
                        while (child != null) {
                            if (child is PsiKeyword && PsiKeyword.STATIC == child.text) {
                                staticElement = child
                                break
                            }
                            child = child.nextSibling
                        }
                    }
                    val location = if (staticElement != null) {
                        context.getLocation(staticElement)
                    } else {
                        context.getNameLocation(getter)
                    }
                    val message =
                        "This getter should not be static such that `$propertyName` can " +
                                "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    context.report(KOTLIN_PROPERTY, setter, location, message)
                    return
                }

                if (setter.uastParameters.first().type != getter?.returnType) {
                    val message =
                        "The getter return type (`${getter?.returnType?.presentableText}`) and setter parameter type (`${setter.uastParameters.first().type.presentableText}`) getter and setter methods for property `$propertyName` should have exactly the same type to allow " +
                                "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    val location = getPropertyLocation(getter!!, setter)
                    context.report(KOTLIN_PROPERTY, setter, location, message)
                    return
                }

                // Make sure that if the getter is inherited, it has the same return type
                for (superMethod in getter.findSuperMethods()) {
                    if (superMethod.containingClass?.isInterface != true) {
                        val superReturnType = superMethod.returnType ?: return
                        val getterType = getter.returnType
                        if (superReturnType != getterType) {
                            val message =
                                "The getter return type (`${getterType?.presentableText}`)" +
                                        " is not the same as the setter return type " +
                                        "(`${superReturnType.presentableText}`); they should have " +
                                        "exactly the same type to allow " +
                                        "`${propertySuffix.decapitalize()}` " +
                                        "be accessed as a property from Kotlin; see " +
                                        "https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                            val location = getPropertyLocation(getter, setter)
                            context.report(KOTLIN_PROPERTY, setter, location, message)
                            return
                        }
                    }
                }
            } else if (badGetter != null) {
                val location = context.getNameLocation(badGetter!!)
                val message =
                    "This method should be called `get$propertySuffix` such that `$propertyName` can " +
                            "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                context.report(KOTLIN_PROPERTY, setter, location, message)
            }
        }

        private fun getPropertyLocation(
            primary: PsiMethod,
            secondary: PsiMethod
        ): Location {
            return context.getNameLocation(primary).withSecondary(
                context.getNameLocation(secondary),
                "${if (secondary.name.startsWith("set")) "Setter" else "Getter"} here"
            )
        }

        private fun ensureNullnessKnown(
            node: UDeclaration,
            type: PsiType
        ) {
            if (type is PsiPrimitiveType) {
                return
            }
            for (annotation in node.annotations) {
                val name = annotation.qualifiedName
                if (name != null && (isNullableAnnotation(name) || isNonNullAnnotation(name))) {
                    return
                }
            }

            val location: Location =
                when (node) {
                    is UVariable -> // UParameter, UField
                        context.getLocation(node.typeReference ?: return)
                    is UMethod -> context.getLocation(node.returnTypeElement ?: return)
                    else -> return
                }
            val replaceLocation = if (node is UParameter) {
                location
            } else if (node is UMethod && node.modifierList != null) {
                // Place the insertion point at the modifiers such that we don't
                // insert the annotation for example after the "public" keyword.
                // We also don't want to place it on the method range itself since
                // that would place it before the method comments.
                context.getLocation(node.modifierList)
            } else if (node is UField && node.modifierList != null) {
                // Ditto for fields
                context.getLocation(node.modifierList!!)
            } else {
                return
            }
            val message = "Unknown nullability; explicitly declare as `@Nullable` or `@NonNull`" +
                    " to improve Kotlin interoperability; see " +
                    "https://android.github.io/kotlin-guides/interop.html#nullability-annotations"
            val fix = LintFix.create().alternatives(
                LintFix.create()
                    .replace()
                    .name("Annotate @NonNull")
                    .range(replaceLocation)
                    .beginning()
                    .shortenNames()
                    .reformat(true)
                    .with("${getNonNullAnnotation(context)} ")
                    .build(),
                LintFix.create()
                    .replace()
                    .name("Annotate @Nullable")
                    .range(replaceLocation)
                    .beginning()
                    .shortenNames()
                    .reformat(true)
                    .with("${getNullableAnnotation(context)} ")
                    .build()
            )
            context.report(PLATFORM_NULLNESS, node as UElement, location, message, fix)
        }

        private fun getNonNullAnnotation(context: JavaContext): String {
            initializeAnnotationNames(context)
            return nonNullAnnotation!!
        }

        private fun getNullableAnnotation(context: JavaContext): String {
            initializeAnnotationNames(context)
            return nullableAnnotation!!
        }

        private var nonNullAnnotation: String? = null
        private var nullableAnnotation: String? = null

        private fun initializeAnnotationNames(context: JavaContext) {
            if (nonNullAnnotation == null) {
                val libraries = GradleDetector.getAndroidLibraries(context.mainProject)
                for (library in libraries) {
                    val coordinates = library.resolvedCoordinates
                    @Suppress("UNNECESSARY_SAFE_CALL") // API has been observed to lie
                    if (coordinates.groupId?.startsWith("androidx") ?: false) {
                        nonNullAnnotation = "@androidx.annotation.NonNull"
                        nullableAnnotation = "@androidx.annotation.Nullable"
                        return
                    }
                }

                nonNullAnnotation = "@android.support.annotation.NonNull"
                nullableAnnotation = "@android.support.annotation.Nullable"
            }
        }

        private fun isNullableAnnotation(qualifiedName: String): Boolean {
            return qualifiedName.endsWith("Nullable")
        }

        private fun isNonNullAnnotation(qualifiedName: String): Boolean {
            return qualifiedName.endsWith("NonNull") ||
                    qualifiedName.endsWith("NotNull") ||
                    qualifiedName.endsWith("Nonnull")
        }

        private fun ensureNonKeyword(name: String, node: UDeclaration, typeLabel: String) {
            if (isKotlinHardKeyword(name)) {
                // See if this method is overriding some other method; in that case
                // we don't have a choice here.
                if (node is UMethod && context.evaluator.isOverride(node)) {
                    return
                }
                val message =
                    "Avoid $typeLabel names that are Kotlin hard keywords (\"$name\"); see " +
                            "https://android.github.io/kotlin-guides/interop.html#no-hard-keywords"
                context.report(
                    NO_HARD_KOTLIN_KEYWORDS,
                    node as UElement,
                    context.getNameLocation(node as UElement),
                    message
                )
            }
        }

        private fun ensureLambdaLastParameter(method: UMethod) {
            val parameters = method.uastParameters
            if (parameters.size > 1) {
                // Make sure that SAM-compatible parameters are last
                val lastIndex = parameters.size - 1
                if (!isFunctionalInterface(parameters[lastIndex].type)) {
                    for (i in lastIndex - 1 downTo 0) {
                        val parameter = parameters[i]
                        if (isFunctionalInterface(parameter.type)) {
                            val message =
                                "Functional interface parameters (such as parameter ${i + 1}, \"${parameter.name}\", in ${
                                method.containingClass?.qualifiedName}.${method.name
                                }) should be last to improve Kotlin interoperability; see " +
                                        "https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions"
                            context.report(
                                LAMBDA_LAST,
                                method,
                                context.getLocation(parameters[lastIndex] as UElement),
                                message
                            )
                            break
                        }
                    }
                }
            }
        }

        private fun isFunctionalInterface(type: PsiType): Boolean {
            if (type !is PsiClassType) {
                return false
            }

            val cls = type.resolve() ?: return false
            if (!cls.isInterface) {
                return false
            }

            var abstractCount = 0
            for (method in cls.methods) {
                if (method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    abstractCount++
                }
            }

            if (abstractCount != 1) {
                // Try a little harder; we don't want to count methods that are overrides
                if (abstractCount > 1) {
                    abstractCount = 0
                    for (method in cls.methods) {
                        if (method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) &&
                            !context.evaluator.isOverride(method, true)
                        ) {
                            abstractCount++
                        }
                    }
                }

                if (abstractCount != 1) {
                    return false
                }
            }

            if (cls.superClass?.isInterface == true) {
                return false
            }

            return true
        }
    }
}