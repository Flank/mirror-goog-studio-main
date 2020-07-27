/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.impl.compiled.ClsAnnotationImpl
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.util.isArrayInitializer
import java.util.Arrays

internal sealed class AnnotationValuesExtractor {

    companion object {
        @JvmStatic
        internal fun getAnnotationValuesExtractor(annotation: UAnnotation?): AnnotationValuesExtractor =
            if (annotation?.javaPsi is ClsAnnotationImpl) Compiled else Source
    }

    internal abstract fun getAnnotationConstantObject(annotation: UAnnotation?, name: String): Any?
    internal fun getAnnotationBooleanValue(annotation: UAnnotation?, name: String): Boolean? =
        getAnnotationConstantObject(annotation, name) as? Boolean

    internal fun getAnnotationLongValue(annotation: UAnnotation?, name: String): Long? =
        (getAnnotationConstantObject(annotation, name) as? Number)?.toLong()

    internal fun getAnnotationDoubleValue(annotation: UAnnotation?, name: String): Double? =
        (getAnnotationConstantObject(annotation, name) as? Number)?.toDouble()

    internal fun getAnnotationStringValue(annotation: UAnnotation?, name: String): String? =
        getAnnotationConstantObject(annotation, name) as? String

    internal abstract fun getAnnotationStringValues(
        annotation: UAnnotation?,
        name: String
    ): Array<String>?

    private object Source : AnnotationValuesExtractor() {
        override fun getAnnotationConstantObject(annotation: UAnnotation?, name: String): Any? =
            annotation?.findDeclaredAttributeValue(name)
                ?.let { ConstantEvaluator.evaluate(null, it) }

        override fun getAnnotationStringValues(
            annotation: UAnnotation?,
            name: String
        ): Array<String>? {
            val attributeValue = annotation?.findDeclaredAttributeValue(name) ?: return null

            if (attributeValue.isArrayInitializer()) {
                val initializers = (attributeValue as UCallExpression).valueArguments

                val constantEvaluator = ConstantEvaluator()
                val result = initializers.stream()
                    .map { e -> constantEvaluator.evaluate(e) }
                    .filter { e -> e is String }
                    .map { e -> e as String }
                    .toArray<String> { size -> arrayOfNulls(size) }

                return result.takeIf { result.isNotEmpty() }?.let { it }
            } else {
                // Use constant evaluator since we want to resolve field references as well
                return when (val o = ConstantEvaluator.evaluate(null, attributeValue)) {
                    is String -> {
                        arrayOf(o)
                    }
                    is Array<*> -> {
                        Arrays.stream(o)
                            .filter { e -> e is String }
                            .map { e -> e as String }
                            .toArray<String> { size -> arrayOfNulls(size) }
                    }
                    else -> {
                        null
                    }
                }
            }
        }
    }

    private object Compiled : AnnotationValuesExtractor() {
        private fun getClsAnnotation(annotation: UAnnotation?): ClsAnnotationImpl? =
            (annotation?.javaPsi) as? ClsAnnotationImpl

        override fun getAnnotationConstantObject(annotation: UAnnotation?, name: String): Any? =
            getClsAnnotation(annotation)?.findDeclaredAttributeValue(name)
                ?.let { ConstantEvaluator.evaluate(null, it) }

        override fun getAnnotationStringValues(
            annotation: UAnnotation?,
            name: String
        ): Array<String>? {
            val clsAnnotation = getClsAnnotation(annotation) ?: return null
            val attribute = clsAnnotation.findDeclaredAttributeValue(name) ?: return null

            if (attribute is PsiArrayInitializerMemberValue) {
                val initializers = attribute.initializers
                val constantEvaluator = ConstantEvaluator()

                val result = Arrays.stream(initializers)
                    .map { e -> constantEvaluator.evaluate(e) }
                    .filter { e -> e is String }
                    .map { e -> e as String }
                    .toArray<String> { size -> arrayOfNulls(size) }

                return result.takeIf { it.isNotEmpty() }?.let { it }
            } else {
                // Use constant evaluator since we want to resolve field references as well
                return when (val o = ConstantEvaluator.evaluate(null, attribute)) {
                    is String -> {
                        arrayOf(o)
                    }
                    is Array<*> -> {
                        Arrays.stream(o)
                            .filter { e -> e is String }
                            .map { e -> e as String }
                            .toArray<String> { size -> arrayOfNulls(size) }
                    }
                    else -> {
                        null
                    }
                }
            }
        }
    }
}
