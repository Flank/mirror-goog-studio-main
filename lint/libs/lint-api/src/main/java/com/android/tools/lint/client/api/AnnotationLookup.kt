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

import com.android.tools.lint.detector.api.isKotlin
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.java.JavaUAnnotation

class AnnotationLookup {
    private val resolvedKotlinClassCache = mutableMapOf<PsiClass, UClass>()

    fun findRealAnnotation(
        annotation: PsiAnnotation,
        resolved: PsiClass,
        context: UElement? = null
    ): UAnnotation {
        var kotlinClass: UClass? = null
        if (isKotlin(resolved.language)) {
            kotlinClass = resolvedKotlinClassCache[resolved] ?: run {
                // We sometimes get binaries out of Kotlin files after a resolve; find the
                // original AST nodes
                val project = resolved.project
                val cls =
                    UastFacade.convertElement(resolved, null, UClass::class.java) as UClass?
                cls?.let {
                    kotlinClass = it
                    resolvedKotlinClassCache[resolved] = it
                }
                cls
            }
        } else if (isKotlin((resolved.containingFile?.virtualFile?.fileType as? LanguageFileType)?.language)) {
            // Work around for weird wrapper classes like LightClassBuilderKt$createJavaFileStub$fakeFile$1
            // which reports a language type of Java instead of Kotlin
            // We sometimes get binaries out of Kotlin files after a resolve; find the
            // original AST nodes
            kotlinClass = resolvedKotlinClassCache[resolved] ?: run {
                val contextFile = context?.psi?.containingFile?.virtualFile
                val target = resolved.containingFile?.virtualFile

                val project = resolved.project
                val uFile: UFile? =
                    if (contextFile != null && contextFile == target) {
                        // Resolved to some element in the same element
                        // compilation unit; in that case we already have a fully
                        // converter UFile
                        context.getContainingUFile()
                    } else {
                        val psiFile =
                            PsiManager.getInstance(project)
                                .findFile(resolved.containingFile?.virtualFile!!)
                        if (psiFile != null) {
                            UastFacade.convertElementWithParent(
                                psiFile,
                                UFile::class.java
                            ) as? UFile
                        } else {
                            null
                        }
                    }

                if (uFile != null) {
                    findClass(uFile, resolved.qualifiedName!!)?.let {
                        kotlinClass = it
                        resolvedKotlinClassCache[resolved] = it
                    }
                }
                kotlinClass
            }
        }

        if (kotlinClass != null) {
            val annotationQualifiedName = annotation.qualifiedName
            if (annotationQualifiedName != null) {
                //noinspection ExternalAnnotations
                for (uAnnotation in (kotlinClass as UAnnotated).uAnnotations) {
                    if (annotationQualifiedName == uAnnotation.qualifiedName) {
                        return uAnnotation
                    }
                }
            }
        }

        return JavaUAnnotation.wrap(annotation)
    }

    private fun findClass(file: UFile?, target: String): UClass? {
        file ?: return null

        for (top in file.classes) {
            val result = findClass(top, target)
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun findClass(cls: UClass, target: String): UClass? {
        if (cls.qualifiedName == target) {
            return cls
        }

        for (inner in cls.innerClasses) {
            val result = findClass(inner, target)
            if (result != null) {
                return result
            }
        }

        return null
    }
}
