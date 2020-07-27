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

package com.android.tools.lint.client.api

import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.DefaultLintModelMavenName
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.google.common.collect.Maps
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.PsiWildcardType
import org.jetbrains.kotlin.asJava.elements.KtLightModifierList
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import java.io.File
import java.util.ArrayList

const val TYPE_OBJECT = "java.lang.Object"
const val TYPE_STRING = "java.lang.String"
const val TYPE_INT = "int"
const val TYPE_LONG = "long"
const val TYPE_CHAR = "char"
const val TYPE_FLOAT = "float"
const val TYPE_DOUBLE = "double"
const val TYPE_BOOLEAN = "boolean"
const val TYPE_SHORT = "short"
const val TYPE_BYTE = "byte"
const val TYPE_NULL = "null"
const val TYPE_INTEGER_WRAPPER = "java.lang.Integer"
const val TYPE_BOOLEAN_WRAPPER = "java.lang.Boolean"
const val TYPE_BYTE_WRAPPER = "java.lang.Byte"
const val TYPE_SHORT_WRAPPER = "java.lang.Short"
const val TYPE_LONG_WRAPPER = "java.lang.Long"
const val TYPE_DOUBLE_WRAPPER = "java.lang.Double"
const val TYPE_FLOAT_WRAPPER = "java.lang.Float"
const val TYPE_CHARACTER_WRAPPER = "java.lang.Character"

abstract // Some of these methods may be overridden by LintClients
class JavaEvaluator {
    abstract val dependencies: LintModelDependencies?

    private var relevantAnnotations: Set<String>? = null

    /**
     * Cache for [.getLibrary]
     */
    private var jarToGroup: MutableMap<String, LintModelMavenName>? = null

    abstract fun extendsClass(
        cls: PsiClass?,
        className: String,
        strict: Boolean = false
    ): Boolean

    abstract fun implementsInterface(
        cls: PsiClass,
        interfaceName: String,
        strict: Boolean = false
    ): Boolean

    open fun isMemberInSubClassOf(
        member: PsiMember,
        className: String,
        strict: Boolean = false
    ): Boolean {
        val containingClass = member.containingClass
        return containingClass != null && extendsClass(containingClass, className, strict)
    }

    open fun isMemberInClass(
        member: PsiMember?,
        className: String
    ): Boolean {
        if (member == null) {
            return false
        }
        val containingClass = member.containingClass
        return containingClass != null && className == containingClass.qualifiedName
    }

    open fun getParameterCount(method: PsiMethod): Int {
        return method.parameterList.parametersCount
    }

    /**
     * Checks whether the class extends a super class or implements a given interface. Like calling
     * both [.extendsClass] and [ ][.implementsInterface].
     */
    open fun inheritsFrom(
        cls: PsiClass?,
        className: String,
        strict: Boolean = false
    ): Boolean {
        cls ?: return false
        return extendsClass(cls, className, strict) || implementsInterface(cls, className, strict)
    }

    /**
     * Returns true if the given method (which is typically looked up by resolving a method call) is
     * either a method in the exact given class, or if `allowInherit` is true, a method in a
     * class possibly extending the given class, and if the parameter types are the exact types
     * specified.
     *
     * @param method the method in question
     * @param className the class name the method should be defined in or inherit from (or
     * if null, allow any class)
     * @param allowInherit whether we allow checking for inheritance
     * @param argumentTypes the names of the types of the parameters
     * @return true if this method is defined in the given class and with the given parameters
     */
    open fun methodMatches(
        method: PsiMethod,
        className: String?,
        allowInherit: Boolean,
        vararg argumentTypes: String
    ): Boolean {
        if (className != null && allowInherit) {
            if (!isMemberInSubClassOf(method, className, false)) {
                return false
            }
        }

        return parametersMatch(method, *argumentTypes)
    }

    /**
     * Returns true if the given method's parameters are the exact types specified.
     *
     * @param method the method in question
     * @param argumentTypes the names of the types of the parameters
     * @return true if this method is defined in the given class and with the given parameters
     */
    open fun parametersMatch(
        method: PsiMethod,
        vararg argumentTypes: String
    ): Boolean {
        val parameterList = method.parameterList
        if (parameterList.parametersCount != argumentTypes.size) {
            return false
        }
        val parameters = parameterList.parameters
        for (i in parameters.indices) {
            val type = parameters[i].type
            if (type.canonicalText != argumentTypes[i]) {
                return false
            }
        }

        return true
    }

    /** Returns true if the given type matches the given fully qualified type name  */
    open fun parameterHasType(
        method: PsiMethod?,
        parameterIndex: Int,
        typeName: String
    ): Boolean {
        if (method == null) {
            return false
        }
        val parameterList = method.parameterList
        return parameterList.parametersCount > parameterIndex && typeMatches(
            parameterList.parameters[parameterIndex].type,
            typeName
        )
    }

    /** Returns true if the given type matches the given fully qualified type name  */
    open fun typeMatches(
        type: PsiType?,
        typeName: String
    ): Boolean {
        return type != null && type.canonicalText == typeName
    }

    open fun resolve(element: PsiElement): PsiElement? {
        if (element is PsiReference) {
            return (element as PsiReference).resolve()
        } else if (element is PsiMethodCallExpression) {
            val resolved = element.resolveMethod()
            if (resolved != null) {
                return resolved
            }
        }
        return null
    }

    open fun isPublic(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.PUBLIC)
        }
        return false
    }

    open fun isProtected(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.PROTECTED)
        }
        return false
    }

    open fun isStatic(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)
        }
        return false
    }

    open fun isPrivate(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.PRIVATE)
        }
        return false
    }

    open fun isAbstract(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.ABSTRACT)
        }
        return false
    }

    @Suppress("unused")
    open fun isFinal(owner: PsiModifierListOwner?): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            return modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)
        }
        return false
    }

    open fun isInternal(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.INTERNAL_KEYWORD)
    }

    open fun isSealed(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.SEALED_KEYWORD)
    }

    open fun isData(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.DATA_KEYWORD)
    }

    open fun isLateInit(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.LATEINIT_KEYWORD)
    }

    open fun isInline(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.INLINE_KEYWORD)
    }

    open fun isOperator(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.OPERATOR_KEYWORD)
    }

    open fun isInfix(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.INFIX_KEYWORD)
    }

    open fun isSuspend(owner: PsiModifierListOwner?): Boolean {
        return hasModifier(owner, KtTokens.SUSPEND_KEYWORD)
    }

    open fun hasModifier(owner: PsiModifierListOwner?, keyword: KtModifierKeywordToken): Boolean {
        if (owner != null) {
            val modifierList = owner.modifierList
            if (modifierList is KtLightModifierList<*>) {
                val ktModifierList = modifierList.kotlinOrigin
                if (ktModifierList != null) {
                    return ktModifierList.hasModifier(keyword)
                }
            }
        }
        return false
    }

    open fun getSuperMethod(method: PsiMethod?): PsiMethod? {
        if (method == null) {
            return null
        }
        val superMethods = method.findSuperMethods()
        if (superMethods.size > 1) {
            // Prefer non-compiled concrete methods
            for (m in superMethods) {
                if (m !is PsiCompiledElement && m.containingClass?.isInterface == false) {
                    return m
                }
            }
            for (m in superMethods) {
                if (m.containingClass?.isInterface == false) {
                    return m
                }
            }
            for (m in superMethods) {
                if (m !is PsiCompiledElement) {
                    return m
                }
            }
        }
        return if (superMethods.isNotEmpty()) {
            superMethods[0]
        } else null
    }

    open fun getQualifiedName(psiClass: PsiClass): String? {
        var qualifiedName = psiClass.qualifiedName
        if (qualifiedName == null) {
            qualifiedName = psiClass.name
            if (qualifiedName == null) {
                assert(psiClass is PsiAnonymousClass)

                return getQualifiedName(psiClass.containingClass!!)
            }
        }
        return qualifiedName
    }

    open fun getQualifiedName(psiClassType: PsiClassType): String? {
        return psiClassType.canonicalText
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "Most lint APIs (such as ApiLookup) no longer require internal JVM names and\n" +
            "      accept qualified names that can be obtained by calling the\n" +
            "      {@link #getQualifiedName(PsiClass)} method."
    )
    open fun getInternalName(psiClass: PsiClass): String? {
        var qualifiedName = psiClass.qualifiedName
        if (qualifiedName == null) {
            qualifiedName = psiClass.name
            if (qualifiedName == null) {
                assert(psiClass is PsiAnonymousClass)

                @Suppress("DEPRECATION")
                return getInternalName(psiClass.containingClass!!)
            }
        }
        return ClassContext.getInternalName(qualifiedName)
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "Most lint APIs (such as ApiLookup) no longer require internal JVM names and\n" +
            "      accept qualified names that can be obtained by calling the\n" +
            "      {@link #getQualifiedName(PsiClassType)} method."
    )
    open fun getInternalName(psiClassType: PsiClassType): String? {
        return ClassContext.getInternalName(psiClassType.canonicalText)
    }

    /**
     * Computes a simplified version of the internal JVM description of the given method. This is in
     * the same format as the ASM desc fields for methods with an exception that the dot ('.')
     * character is used instead of slash ('/') and dollar sign ('$') characters. For example,
     * a method named "foo" that takes an int and a String and returns a void will have description
     * `foo(ILjava.lang.String;):V`.
     *
     * @param method the method to look up the description for
     * @param includeName whether the name should be included
     * @param includeReturn whether the return type should be included
     * @return a simplified version of the internal JVM description for the method
     */
    open fun getMethodDescription(
        method: PsiMethod,
        includeName: Boolean,
        includeReturn: Boolean
    ): String? {
        assert(!includeName) // not yet tested
        assert(!includeReturn) // not yet tested

        val signature = StringBuilder()

        if (includeName) {
            if (method.isConstructor) {
                val declaringClass = method.containingClass
                if (declaringClass != null) {
                    val outerClass = declaringClass.containingClass
                    if (outerClass != null) {
                        // declaring class is an inner class
                        if (!declaringClass.hasModifierProperty(PsiModifier.STATIC)) {
                            if (!appendJvmEquivalentTypeName(signature, outerClass)) {
                                return null
                            }
                        }
                    }
                }
                signature.append(CONSTRUCTOR_NAME)
            } else {
                signature.append(method.name)
            }
        }

        signature.append('(')

        for (psiParameter in method.parameterList.parameters) {
            if (!appendJvmEquivalentSignature(signature, psiParameter.type)) {
                return null
            }
        }
        signature.append(')')
        if (includeReturn) {
            if (!method.isConstructor) {
                if (!appendJvmEquivalentSignature(signature, method.returnType)) {
                    return null
                }
            } else {
                signature.append('V')
            }
        }
        return signature.toString()
    }

    /**
     * Constructs a simplified version of the internal JVM description of the given method. This is
     * in the same format as {@link #getMethodDescription} above, the difference being we don't have
     * the actual PSI for the method type, we just construct the signature from the [method] name,
     * the list of [argumentTypes] and optionally include the [returnType].
     */
    open fun constructMethodDescription(
        method: String,
        includeName: Boolean = false,
        argumentTypes: Array<PsiType>,
        returnType: PsiType? = null,
        includeReturn: Boolean = false
    ): String? = buildString {
        if (includeName) {
            append(method)
        }
        append('(')
        for (argumentType in argumentTypes) {
            if (!appendJvmEquivalentSignature(this, argumentType)) {
                return null
            }
        }
        append(')')
        if (includeReturn) {
            if (!appendJvmEquivalentSignature(this, returnType)) {
                return null
            }
        }
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        "Most lint APIs (such as ApiLookup) no longer require internal JVM method\n" +
            "      descriptions and accept JVM equivalent descriptions that can be obtained by calling the\n" +
            "      {@link #getMethodDescription} method."
    )
    open fun getInternalDescription(
        method: PsiMethod,
        includeName: Boolean,
        includeReturn: Boolean
    ): String? {
        assert(!includeName) // not yet tested
        assert(!includeReturn) // not yet tested

        val signature = StringBuilder()

        if (includeName) {
            if (method.isConstructor) {
                val declaringClass = method.containingClass
                if (declaringClass != null) {
                    val outerClass = declaringClass.containingClass
                    if (outerClass != null) {
                        // declaring class is an inner class
                        if (!declaringClass.hasModifierProperty(PsiModifier.STATIC)) {
                            @Suppress("DEPRECATION")
                            if (!appendJvmTypeName(signature, outerClass)) {
                                return null
                            }
                        }
                    }
                }
                signature.append(CONSTRUCTOR_NAME)
            } else {
                signature.append(method.name)
            }
        }

        signature.append('(')

        for (psiParameter in method.parameterList.parameters) {
            @Suppress("DEPRECATION")
            if (!appendJvmSignature(signature, psiParameter.type)) {
                return null
            }
        }
        signature.append(')')
        if (includeReturn) {
            if (!method.isConstructor) {
                @Suppress("DEPRECATION")
                if (!appendJvmSignature(signature, method.returnType)) {
                    return null
                }
            } else {
                signature.append('V')
            }
        }
        return signature.toString()
    }

    /**
     * The JVM equivalent type name differs from the real JVM name by using dot ('.') instead of
     * slash ('/') and dollar sign ('$') characters.
     */
    private fun appendJvmEquivalentTypeName(
        signature: StringBuilder,
        outerClass: PsiClass
    ): Boolean {
        val className = getQualifiedName(outerClass) ?: return false
        signature.append('L').append(className).append(';')
        return true
    }

    /**
     * The JVM equivalent signature differs from the real JVM signature by using dot ('.') instead
     * of slash ('/') and dollar sign ('$') characters.
     */
    private fun appendJvmEquivalentSignature(
        buffer: StringBuilder,
        type: PsiType?
    ): Boolean {
        if (type == null) {
            return false
        }

        val psiType = erasure(type)

        if (psiType is PsiArrayType) {
            buffer.append('[')
            appendJvmEquivalentSignature(buffer, psiType.componentType)
        } else if (psiType is PsiClassType) {
            val resolved = psiType.resolve() ?: return false
            if (!appendJvmEquivalentTypeName(buffer, resolved)) {
                return false
            }
        } else if (psiType is PsiPrimitiveType) {
            buffer.append(getPrimitiveSignature(psiType.canonicalText))
        } else {
            return false
        }
        return true
    }

    @Deprecated("")
    private fun appendJvmTypeName(
        signature: StringBuilder,
        outerClass: PsiClass
    ): Boolean {
        @Suppress("DEPRECATION")
        val className = getInternalName(outerClass) ?: return false
        signature.append('L').append(className).append(';')
        return true
    }

    @Deprecated("")
    private fun appendJvmSignature(
        buffer: StringBuilder,
        type: PsiType?
    ): Boolean {
        if (type == null) {
            return false
        }

        val psiType = erasure(type)

        when (psiType) {
            is PsiArrayType -> {
                buffer.append('[')
                @Suppress("DEPRECATION")
                appendJvmSignature(buffer, psiType.componentType)
            }
            is PsiClassType -> {
                val resolved = psiType.resolve() ?: return false
                @Suppress("DEPRECATION")
                if (!appendJvmTypeName(buffer, resolved)) {
                    return false
                }
            }
            is PsiPrimitiveType -> buffer.append(getPrimitiveSignature(psiType.canonicalText))
            else -> return false
        }
        return true
    }

    open fun areSignaturesEqual(method1: PsiMethod, method2: PsiMethod): Boolean {
        val parameterList1 = method1.parameterList
        val parameterList2 = method2.parameterList
        if (parameterList1.parametersCount != parameterList2.parametersCount) {
            return false
        }

        val parameters1 = parameterList1.parameters
        val parameters2 = parameterList2.parameters

        var i = 0
        val n = parameters1.size
        while (i < n) {
            val parameter1 = parameters1[i]
            val parameter2 = parameters2[i]
            var type1: PsiType? = parameter1.type
            var type2: PsiType? = parameter2.type
            if (type1 != type2) {
                type1 = erasure(parameter1.type)
                type2 = erasure(parameter2.type)
                if (type1 != type2) {
                    return false
                }
            }
            i++
        }

        return true
    }

    open fun erasure(type: PsiType?): PsiType? {
        return type?.accept(object : PsiTypeVisitor<PsiType>() {
            override fun visitType(type: PsiType): PsiType? {
                return type
            }

            override fun visitClassType(classType: PsiClassType): PsiType? {
                return classType.rawType()
            }

            override fun visitWildcardType(wildcardType: PsiWildcardType): PsiType? {
                return wildcardType
            }

            override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): PsiType? {
                return primitiveType
            }

            override fun visitEllipsisType(ellipsisType: PsiEllipsisType): PsiType? {
                val componentType = ellipsisType.componentType
                val newComponentType = componentType.accept(this)
                return if (newComponentType === componentType) ellipsisType else newComponentType?.createArrayType()
            }

            override fun visitArrayType(arrayType: PsiArrayType): PsiType? {
                val componentType = arrayType.componentType
                val newComponentType = componentType.accept(this)
                return if (newComponentType === componentType) arrayType else newComponentType?.createArrayType()
            }
        })
    }

    abstract fun findClass(qualifiedName: String): PsiClass?

    abstract fun getClassType(psiClass: PsiClass?): PsiClassType?

    abstract fun getTypeClass(psiType: PsiType?): PsiClass?

    abstract fun getAllAnnotations(
        owner: UAnnotated,
        inHierarchy: Boolean
    ): List<UAnnotation>

    abstract fun getAllAnnotations(
        owner: PsiModifierListOwner,
        inHierarchy: Boolean
    ): Array<PsiAnnotation>

    abstract fun findAnnotationInHierarchy(
        listOwner: PsiModifierListOwner,
        vararg annotationNames: String
    ): PsiAnnotation?

    abstract fun findAnnotation(
        listOwner: PsiModifierListOwner?,
        vararg annotationNames: String
    ): PsiAnnotation?

    /**
     * Try to determine the path to the .jar file containing the element, **if** applicable
     */
    abstract fun findJarPath(element: PsiElement): String?

    /**
     * Try to determine the path to the .jar file containing the element, **if** applicable
     */
    abstract fun findJarPath(element: UElement): String?

    /**
     * Returns true if the given annotation is inherited (instead of being defined directly
     * on the given modifier list holder
     *
     * @param annotation the annotation to check
     * @param owner the owner potentially declaring the annotation
     * @return true if the annotation is inherited rather than being declared directly on this owner
     */
    open fun isInherited(
        annotation: PsiAnnotation,
        owner: PsiModifierListOwner
    ): Boolean {
        val annotationOwner = annotation.owner
        return annotationOwner == null || annotationOwner != owner.modifierList
    }

    open fun isInherited(
        annotation: UAnnotation,
        owner: PsiModifierListOwner
    ): Boolean {
        val psi = annotation.psi
        if (psi is PsiAnnotation) {
            val annotationOwner = psi.owner
            return annotationOwner == null || annotationOwner != owner.modifierList
        } else if (psi is KtAnnotationEntry) {
            val parent = psi.getParentOfType<KtModifierListOwner>(true)
                ?.let { if (it is KtPropertyAccessor) it.property else it }
            val ownerPsi = if (owner is UElement)
                owner.sourcePsi
            else
                owner
            return parent == null || parent != ownerPsi
        }

        return true
    }

    /**
     * Returns true if the given annotation is inherited (instead of being defined directly
     * on the given modifier list holder
     *
     * @param annotation the annotation to check
     * @param owner the owner potentially declaring the annotation
     * @return true if the annotation is inherited rather than being declared directly on this owner
     */
    @SuppressWarnings("ExternalAnnotations")
    open fun isInherited(annotation: UAnnotation, owner: UAnnotated): Boolean {
        return owner.uAnnotations.contains(annotation)
    }

    abstract fun getPackage(node: PsiElement): PsiPackage?

    abstract fun getPackage(node: UElement): PsiPackage?

    // Just here to disambiguate getPackage(PsiElement) and getPackage(UElement) since
    // a UMethod is both a PsiElement and a UElement
    open fun getPackage(node: UMethod): PsiPackage? {
        return getPackage(node as PsiElement)
    }

    /** Returns the Lint project containing the given element */
    open fun getProject(element: PsiElement): Project? {
        return null
    }

    /**
     * Return the Gradle group id for the given element, **if** applicable. For example, for
     * a method in the appcompat library, this would return "com.android.support".
     */
    open fun getLibrary(element: PsiElement): LintModelMavenName? {
        if (element !is PsiCompiledElement) {
            return getProject(element)?.mavenCoordinate
        }
        return getLibrary(findJarPath(element))
    }

    /**
     * Return the Gradle group id for the given element, **if** applicable. For example, for
     * a method in the appcompat library, this would return "com.android.support".
     */
    open fun getLibrary(element: UElement): LintModelMavenName? {
        if (element !is PsiCompiledElement) {
            val psi = element.sourcePsi
            return if (psi != null) {
                getProject(psi)?.mavenCoordinate
            } else {
                null
            }
        }
        return getLibrary(findJarPath(element as UElement))
    }

    /** Disambiguate between UElement and PsiElement since a UMethod is both  */
    @Suppress("unused")
    open fun getLibrary(element: UMethod): LintModelMavenName? {
        return getLibrary(element as PsiElement)
    }

    fun getLibrary(file: File): LintModelMavenName? {
        return getLibrary(file.path)
    }

    private fun getLibrary(jarFile: String?): LintModelMavenName? {
        if (jarFile != null) {
            if (jarToGroup == null) {
                jarToGroup = Maps.newHashMap()
            }
            var coordinates: LintModelMavenName? = jarToGroup!![jarFile]
            if (coordinates == null) {
                val library = findOwnerLibrary(jarFile.replace('/', File.separatorChar))
                if (library != null && library is LintModelExternalLibrary) {
                    coordinates = library.resolvedCoordinates
                }
                if (coordinates == null) {
                    // Use string location to figure it out. Note however that
                    // this doesn't work when the build cache is in effect.
                    // Example:
                    // $PROJECT_DIRECTORY/app/build/intermediates/exploded-aar/com.android.support/
                    //          /appcompat-v7/25.0.0-SNAPSHOT/jars/classes.jar
                    // and we want to pick out "com.android.support" and "appcompat-v7"
                    var index = jarFile.indexOf("exploded-aar")
                    if (index != -1) {
                        index += 13 // "exploded-aar/".length()
                        var i = index
                        while (i < jarFile.length) {
                            var c = jarFile[i]
                            if (c == '/' || c == File.separatorChar) {
                                val groupId = jarFile.substring(index, i)
                                i++
                                for (j in i until jarFile.length) {
                                    c = jarFile[j]
                                    if (c == '/' || c == File.separatorChar) {
                                        val artifactId = jarFile.substring(i, j)
                                        val versionEnd = jarFile.indexOf(c, j + 1)
                                        val version = if (versionEnd != -1) jarFile.substring(
                                            j + 1,
                                            versionEnd
                                        ) else ""
                                        coordinates = DefaultLintModelMavenName(
                                            groupId,
                                            artifactId,
                                            version
                                        )
                                        break
                                    }
                                }
                                break
                            }
                            i++
                        }
                    }
                }
                if (coordinates == null) {
                    coordinates = LintModelMavenName.NONE
                }
                jarToGroup!![jarFile] = coordinates
            }
            return if (coordinates === LintModelMavenName.NONE) null else coordinates
        }

        return null
    }

    open fun findOwnerLibrary(jarFile: String): LintModelLibrary? {
        val dependencies = dependencies
        if (dependencies != null) {
            val match = findOwnerLibrary(dependencies.getAll(), jarFile)
            if (match != null) {
                return match
            }

            // Fallback: There are cases, particularly on Windows (see issue 70565382) where we end up with
            // a mismatch with what is in the model and which class file paths in the gradle cache
            // are used. For example, we might be looking for
            // C:\Users\studio\.gradle\caches\transforms-1\files-1.1\mylibrary-release.aar\9a90779305f6d83489fbb0d005980e33\jars\classes.jar
            // but the builder-model dependencies points to this:
            // C:\Users\studio\.gradle\caches\transforms-1\files-1.1\mylibrary-release.aar\cb3fd10cf216826d2aa7a59f23e8f35c\jars\classes.jar
            // To work around this, if there aren't any matches among the dependencies, we do a second search
            // where we match paths by skipping the checksum in the middle of the path:
            if (jarFile.contains(".gradle")) {
                val aar = jarFile.indexOf(".aar" + File.separator)
                if (aar != -1) {
                    val prefixEnd = aar + 5
                    val suffixStart = jarFile.indexOf(File.separatorChar, prefixEnd + 1)
                    if (suffixStart != -1) {
                        val prefix = jarFile.substring(0, prefixEnd)
                        val suffix = jarFile.substring(suffixStart)

                        val prefixMatch = findOwnerLibrary(dependencies.getAll(), prefix, suffix)
                        if (prefixMatch != null) {
                            return prefixMatch
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findOwnerLibrary(
        dependencies: Collection<LintModelLibrary>,
        jarFile: String
    ): LintModelLibrary? {
        for (library in dependencies.asSequence().filterIsInstance<LintModelExternalLibrary>()) {
            for (jar in library.jarFiles) {
                if (jarFile == jar.path) {
                    return library
                }
            }
        }

        return null
    }

    private fun findOwnerLibrary(
        dependencies: Collection<LintModelLibrary>,
        pathPrefix: String,
        pathSuffix: String
    ): LintModelLibrary? {
        for (library in dependencies.asSequence().filterIsInstance<LintModelExternalLibrary>()) {
            for (jar in library.jarFiles) {
                val path = jar.path
                if (path.startsWith(pathPrefix) && path.endsWith(pathSuffix)) {
                    return library
                }
            }
        }

        return null
    }

    /**
     * For a given call, computes the argument to parameter mapping. For Java
     * this is generally one to one (except for varargs), but in Kotlin it can be
     * quite a bit more complicated due to extension methods, named parameters,
     * default parameters, and varargs and the spread operator.
     */
    open fun computeArgumentMapping(
        call: UCallExpression,
        method: PsiMethod
    ): Map<UExpression, PsiParameter> {
        return emptyMap()
    }

    internal fun setRelevantAnnotations(relevantAnnotations: Set<String>?) {
        this.relevantAnnotations = relevantAnnotations
    }

    /**
     * Filters the set of annotations down to those considered by lint (and more importantly,
     * handles indirection, e.g. a custom annotation annotated with a known annotation will
     * return the known annotation instead. For example, if you make an annotation named
     * `@Duration` and annotate it with `@IntDef(a,b,c)`, this method will return
     * the `@IntDef` annotation instead of `@Duration` for the element annotated
     * with a duration.
     */
    open fun filterRelevantAnnotations(annotations: Array<PsiAnnotation>): Array<PsiAnnotation> {
        if (relevantAnnotations == null) {
            return PsiAnnotation.EMPTY_ARRAY
        }
        var result: MutableList<PsiAnnotation>? = null
        val length = annotations.size
        if (length == 0) {
            return annotations
        }
        for (annotation in annotations) {
            val signature = annotation.qualifiedName
            if (signature == null || signature.startsWith("java.") && !relevantAnnotations!!.contains(
                signature
            )
            ) {
                // @Override, @SuppressWarnings etc. Ignore
                continue
            }

            if (relevantAnnotations!!.contains(signature)) {
                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return annotations
                }
                if (result == null) {
                    result = ArrayList(2)
                }
                result.add(annotation)
                continue
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup
            val ref = annotation.nameReferenceElement ?: continue
            val resolved = ref.resolve()
            if (resolved !is PsiClass || !resolved.isAnnotationType) {
                continue
            }
            val cls = resolved as PsiClass?
            val innerAnnotations = getAllAnnotations(cls!!, false)
            for (j in innerAnnotations.indices) {
                val inner = innerAnnotations[j]
                val a = inner.qualifiedName
                if (a != null && relevantAnnotations!!.contains(a)) {
                    if (length == 1 && j == innerAnnotations.size - 1 && result == null) {
                        return innerAnnotations
                    }
                    if (result == null) {
                        result = ArrayList(2)
                    }
                    result.add(inner)
                }
            }
        }

        return if (result != null)
            result.toTypedArray()
        else PsiAnnotation.EMPTY_ARRAY
    }

    /**
     * Returns true if this method is overriding a method from a super class, or
     * optionally if it is implementing a method from an interface
     */
    fun isOverride(method: UMethod, includeInterfaces: Boolean = true): Boolean {
        if (isStatic(method)) {
            return false
        }

        if (isPublic(method) || isProtected(method)) {
            val cls = method.getContainingUClass() ?: return false
            val superCls = cls.superClass ?: return false

            if (includeInterfaces) {
                val superMethods = method.findSuperMethods()
                return superMethods.isNotEmpty()
            }

            val superMethod = superCls.findMethodBySignature(method.psi, true)
            return superMethod != null
        }

        return false
    }

    /**
     * Returns true if this method is overriding a method from a super class, or
     * optionally if it is implementing a method from an interface
     */
    fun isOverride(method: PsiMethod, includeInterfaces: Boolean = true): Boolean {
        if (isStatic(method)) {
            return false
        }

        if (isPublic(method) || isProtected(method)) {
            val cls = method.containingClass ?: return false
            val superCls = cls.superClass ?: return false

            if (includeInterfaces) {
                val superMethods = method.findSuperMethods()
                return superMethods.isNotEmpty()
            }

            val superMethod = superCls.findMethodBySignature(method, true)
            return superMethod != null
        }

        return false
    }

    companion object {
        fun getPrimitiveSignature(typeName: String): String? = when (typeName) {
            "boolean" -> "Z"
            "byte" -> "B"
            "char" -> "C"
            "short" -> "S"
            "int" -> "I"
            "long" -> "J"
            "float" -> "F"
            "double" -> "D"
            "void" -> "V"
            else -> null
        }
    }
}
