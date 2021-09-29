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

package com.android.tools.lint.client.api

import com.intellij.psi.PsiAnnotation
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/**
 * General support for annotations in the Android platform source
 * code (which are in the `android.annotation` package rather than
 * `androidx.annotation`).
 */
class AndroidPlatformAnnotations(
    qualifiedName: String,
    private val delegate: UAnnotation
) : UAnnotation by delegate {
    override val qualifiedName = toAndroidxAnnotation(qualifiedName)

    companion object {
        const val ANDROIDX_ANNOTATIONS_PREFIX = "androidx.annotation."
        const val PLATFORM_ANNOTATIONS_PREFIX = "android.annotation."

        /**
         * Returns true if the given [qualifiedName] represents one of
         * the hidden annotations in the Android platform soure code.
         */
        @JvmStatic
        fun isPlatformAnnotation(qualifiedName: String): Boolean {
            return qualifiedName.startsWith(PLATFORM_ANNOTATIONS_PREFIX)
        }

        /**
         * Converts the given AndroidX annotation's [qualifiedName] into
         * the qualified name for the corresponding hidden annotation in
         * the Android platform source code.
         */
        @JvmStatic
        fun toPlatformAnnotation(qualifiedName: String): String {
            return if (qualifiedName.startsWith(ANDROIDX_ANNOTATIONS_PREFIX)) {
                PLATFORM_ANNOTATIONS_PREFIX + qualifiedName.substring(ANDROIDX_ANNOTATIONS_PREFIX.length)
            } else qualifiedName
        }

        /**
         * Converts the given Android platform source code hidden
         * annotation qualified name into the corresponding AndroidX
         * annotation qualified name.
         */
        @JvmStatic
        fun toAndroidxAnnotation(qualifiedName: String): String {
            return if (qualifiedName.startsWith(PLATFORM_ANNOTATIONS_PREFIX)) {
                ANDROIDX_ANNOTATIONS_PREFIX + qualifiedName.substring(PLATFORM_ANNOTATIONS_PREFIX.length)
            } else qualifiedName
        }

        /**
         * For an annotation that is in the `android.annotation`
         * package, returns a corresponding annotation which reports
         * itself to be in the `androidx.annotation` package instead.
         */
        fun UAnnotation.fromPlatformAnnotation(signature: String? = null): UAnnotation {
            val qualifiedName = signature ?: this.qualifiedName!!
            assert(isPlatformAnnotation(qualifiedName))
            return AndroidPlatformAnnotations(qualifiedName, this)
        }

        /**
         * For an annotation that is in the `android.annotation`
         * package, returns a corresponding annotation which reports
         * itself to be in the `androidx.annotation` package instead.
         */
        fun PsiAnnotation.fromPlatformAnnotation(signature: String? = null): UAnnotation {
            val qualifiedName = signature ?: this.qualifiedName!!
            assert(isPlatformAnnotation(qualifiedName))
            return AndroidPlatformAnnotations(qualifiedName, toUElement(UAnnotation::class.java)!!)
        }
    }
}
