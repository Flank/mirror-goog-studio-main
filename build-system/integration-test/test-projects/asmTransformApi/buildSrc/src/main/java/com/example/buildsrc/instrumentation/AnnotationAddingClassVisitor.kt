/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.example.buildsrc.instrumentation

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * Adds annotations with [annotationDescriptor] to any method with a name that is contained in
 * [methodNamesToBeAnnotated].
 */
class AnnotationAddingClassVisitor(
    private val methodNamesToBeAnnotated: Set<String>,
    private val annotationDescriptor: String,
    apiVersion: Int,
    cv: ClassVisitor
) : ClassVisitor(apiVersion, cv) {

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        // This is to make sure that the InterfaceAddingClassVisitor didn't visit the class before
        // this visitor
        if (interfaces?.any { it.endsWith("InstrumentedInterface") } == true) {
            throw RuntimeException(
                "InterfaceAddingClassVisitor shouldn't visit before AnnotationAddingClassVisitor"
            )
        }
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val mv =
            super.visitMethod(access, name, descriptor, signature, exceptions)
        return if (methodNamesToBeAnnotated.contains(name)) {
            object : MethodVisitor(api, mv) {
                private var visitedAnnotation = false

                override fun visitCode() {
                    visitAnnotation(annotationDescriptor, true)
                    visitedAnnotation = true
                    super.visitCode()
                }

                override fun visitEnd() {
                    // We don't visit code in interfaces
                    if (!visitedAnnotation) {
                        visitAnnotation(annotationDescriptor, true)
                    }
                    super.visitEnd()
                }
            }
        } else mv
    }
}
