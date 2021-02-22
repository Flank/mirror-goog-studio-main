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

package com.android.tools.lint.checks

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

/** Unpacked version of `@androidx.annotation.ChecksSdkIntAtLeast` */
class SdkIntAnnotation(
    val api: Int?,
    val codename: String?,
    val parameter: Int?,
    val lambda: Int?
) {
    constructor(annotation: PsiAnnotation) : this(
        annotation.getAnnotationIntValue("api"),
        annotation.getAnnotationStringValue("codename"),
        annotation.getAnnotationIntValue("parameter"),
        annotation.getAnnotationIntValue("lambda")
    )

    /**
     * Returns the API level for this annotation in the given context.
     */
    fun getApiLevel(
        evaluator: JavaEvaluator,
        owner: PsiModifierListOwner,
        call: UCallExpression?
    ): Int? {
        val apiLevel = apiLevel()
        if (apiLevel != null) {
            return apiLevel
        }

        val index = parameter ?: lambda ?: return null
        if (owner is PsiMethod && call != null) {
            val argument = findArgumentFor(evaluator, owner, index, call)
            if (argument != null) {
                val v = ConstantEvaluator.evaluate(null, argument)
                return (v as? Number)?.toInt()
            }
        }

        return null
    }

    private fun apiLevel(): Int? {
        return if (codename != null && codename.isNotEmpty()) {
            val level = SdkVersionInfo.getApiByBuildCode(codename, false)
            if (level != -1) {
                level
            } else {
                SdkVersionInfo.getApiByPreviewName(codename, true)
            }
        } else if (api != -1) {
            api
        } else {
            null
        }
    }

    private fun findArgumentFor(
        evaluator: JavaEvaluator,
        calledMethod: PsiMethod,
        parameterIndex: Int,
        call: UCallExpression
    ): UExpression? {
        val parameters = calledMethod.parameterList.parameters
        if (parameterIndex >= 0 && parameterIndex < parameters.size) {
            val target = parameters[parameterIndex]
            val mapping = evaluator.computeArgumentMapping(call, calledMethod)
            for ((key, value1) in mapping) {
                if (value1 === target || value1.isEquivalentTo(target)) {
                    return key
                }
            }
        }

        return null
    }

    companion object {
        /**
         * Looks up the @ChecksSdkIntAtLeast annotation for the given
         * method or field.
         */
        fun get(owner: PsiModifierListOwner): SdkIntAnnotation? {
            val annotation = AnnotationUtil.findAnnotation(
                owner, true, VersionChecks.CHECKS_SDK_INT_AT_LEAST_ANNOTATION
            ) ?: return null
            return SdkIntAnnotation(annotation)
        }
    }
}
