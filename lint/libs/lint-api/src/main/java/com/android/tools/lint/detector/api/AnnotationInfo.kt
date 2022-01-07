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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

/**
 * Information about an [annotation] (with the given [qualifiedName]
 * relevant to a given [annotated] element, associated with the given
 * [origin]). For example, given this code
 *
 * ```
 * @MyAnnotation
 * class MyClass {
 *     fun method()
 * }
 * ```
 *
 * for a call to `myClass.method()`, the [AnnotationInfo] would point to
 * `@MyAnnotation`, the owner would be the `method` and the origin would
 * be [AnnotationOrigin.CLASS].
 */
class AnnotationInfo(
    /** The annotation itself (which can provide annotation values) */
    val annotation: UAnnotation,
    /**
     * The qualified name of the annotation, a non-nullable version of
     * [UAnnotation.qualifiedName]
     */
    val qualifiedName: String,
    /** The element that we looked up the annotation for. */
    val annotated: PsiElement?,
    /**
     * The source of the annotation, such as [AnnotationOrigin.FILE] if
     * this annotation was from a `@file:` annotation surrounding the
     * target element.
     */
    val origin: AnnotationOrigin
) {
    /**
     * Returns true if this annotation is inherited from the hierarchy
     * instead of being annotated directly on the [annotated] element.
     */
    fun isInherited(): Boolean {
        // Some annotations should not be treated as inherited though
        // the hierarchy: if that's the case for this annotation in
        // this scanner, check whether it's inherited and if so, skip it
        @Suppress("UElementAsPsi")
        val annotated = annotated
        // First try to look by directly checking the owner element of
        // the annotation.
        // NB: Both JavaUAnnotation and KotlinUAnnotation have `javaPsi` of `PsiAnnotation` type.
        val annotationOwner = annotation.javaPsi?.owner
            ?: annotation.uastParent?.sourcePsi
        val ownerPsi =
            if (annotationOwner is PsiElement) {
                PsiTreeUtil.getParentOfType(
                    annotationOwner,
                    PsiModifierListOwner::class.java,
                    false
                ) ?: annotationOwner
            } else {
                annotationOwner
            }
        if (ownerPsi != null) {
            if (ownerPsi == annotated || ownerPsi == (annotated as? UElement)?.sourcePsi ||
                ownerPsi == (annotated as? KtLightElement<*, *>)?.kotlinOrigin ||
                ownerPsi is KtLightField && annotated is KtLightMember<*> &&
                ownerPsi.kotlinOrigin == annotated.lightMemberOrigin?.originalElement
            ) {
                return false
            }

            if (annotated is PsiPackage || (annotated as? UElement)?.sourcePsi is PsiPackage) {
                return false
            }
            return true
        }
        return false
    }

    override fun toString(): String {
        return "@$qualifiedName from $origin"
    }
}

/**
 * Enum associated with an [AnnotationInfo] which describes where an
 * annotation in the hierarchy originated from, such as a member, the
 * surrounding class, or a surrounding outer class, etc.
 */
enum class AnnotationOrigin {
    /**
     * When an annotation is visited on its own (via
     * [AnnotationUsageType.DEFINITION]) the origin is itself.
     */
    SELF,

    /** The annotation appeared on a parameter. */
    PARAMETER,

    /** The annotation appeared on a method. */
    METHOD,

    /** The annotation appeared on a variable declaration. */
    VARIABLE,

    /** The annotation appeared on a field. */
    FIELD,

    /** The annotation appeared on a property declaration. */
    PROPERTY,

    /** The annotation appeared on a class. */
    CLASS,

    /** The annotation appeared on an outer class. */
    OUTER_CLASS,

    /** The annotation appeared on a compilation unit / file. */
    FILE,

    /** The annotation appeared on a package declaration. */
    PACKAGE,

    /**
     * The annotation appeared on a Kotlin constructor property or class
     * body property without specifying a use site. Technically, this
     * means that it typically applies only to the parameter itself (for
     * constructor properties), or the backing field itself (for class
     * properties), and not the getters and setters of the property,
     * but that's often what developers expect. Lint will also look for
     * these annotations and include them, and their origin is then set
     * to [PROPERTY_DEFAULT].
     */
    PROPERTY_DEFAULT
}

/**
 * Information about an annotation hierarchy. This is provided as a
 * parameter to [SourceCodeScanner.visitAnnotationUsage] instead of as
 * individual parameters such that we can add useful information over
 * time without breaking compatibility.
 */
class AnnotationUsageInfo(
    /**
     * The [annotations] list contains all relevant annotations at the
     * given [usage] site, but this [index] points to the specific
     * annotation info to consider. The actual `annotations[index]`
     * value is directly provided as the `annotationInfo` parameter to
     * [SourceCodeScanner.visitAnnotationUsage], but the index allows
     * you to consider other annotations elsewhere in the hierarchy;
     * the ones with lower indices are in closer scope. For example,
     * if you have registered an interest in `@ThreadSafe` and the
     * callback notifies you that a call is pointing to a method which
     * is associated with a `@ThreadSafe` annotation, it may be the case
     * that this annotation is arriving from a class level annotation,
     * and that there is an annotation on the specific method which
     * counteracts the thread safety annotation, e.g. `@UiThread`.
     * Therefore, you can check all the [annotations] from 0 up to (but
     * not including) index to see if any of those annotations are
     * thread related and if so, ignore this annotation as "hidden".
     */
    var index: Int,
    /**
     * The full set of annotations in the hierarchy (e.g. on the method,
     * its class, any outer classes, at the file level, and in the
     * package level), *in scope order*.
     */
    val annotations: List<AnnotationInfo>,
    /**
     * The actual AST element associated with the [referenced] element.
     * For example, if you have a call to a method annotated with
     * `@ThreadSafe`, then [usage] is the call node and [referenced] is
     * the method.
     */
    val usage: UElement,
    /**
     * The element referenced by [usage], e.g. in a call to a method
     * that was annotated (or is scoped inside another annotated element
     * such as a class), this is the method.
     */
    val referenced: PsiElement?,
    /**
     * The type of annotation usage, which expresses how the [usage]
     * element is associated with the annotation.
     */
    var type: AnnotationUsageType
) {

    /**
     * Returns true if the current annotation (pointed to by [index])
     * is preceded by any other annotation that matches the given
     * [condition]
     */
    fun anyCloser(condition: (AnnotationInfo) -> Boolean): Boolean =
        findCloser(condition) != null

    /**
     * Returns the first annotation matching [condition] that is closer
     * to the reference site.
     */
    fun findCloser(condition: (AnnotationInfo) -> Boolean): AnnotationInfo? {
        for (i in 0 until index) {
            val annotationInfo = annotations[i]
            if (condition(annotationInfo)) {
                return annotationInfo
            }
        }
        return null
    }

    /**
     * Returns true if there is any other annotation on the same
     * annotated element where the [condition] is true
     */
    fun anySameScope(condition: (AnnotationInfo) -> Boolean): Boolean =
        findSameScope(condition) != null

    /**
     * Returns the first annotation which shares the same origin as the
     * current annotation where the [condition] is also true
     */
    fun findSameScope(condition: (AnnotationInfo) -> Boolean): AnnotationInfo? {
        val source = annotations[index].annotated
        for (i in annotations.indices) {
            if (i == index) {
                continue
            }
            val annotation = annotations[i]
            if (annotation.annotated === source && condition(annotation)) {
                return annotation
            }
        }
        return null
    }

    override fun toString(): String {
        return this::class.java.simpleName + "($type for $usage, index=$index in $annotations)"
    }
}
