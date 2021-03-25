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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.android.tools.lint.client.api

import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_CLASS
import com.google.common.io.ByteStreams
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM7
import org.objectweb.asm.Type
import java.io.File
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Integer
import java.lang.Long
import java.lang.Short
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.jar.JarFile

/**
 * Given a lint jar file, checks to see if the jar file looks compatible
 * with the current version of lint.
 */
class LintJarVerifier : ClassVisitor(ASM7) {
    /**
     * Is the class with the given [internal] class name part of an API
     * we want to check for validity?
     */
    private fun isRelevantApi(internal: String): Boolean {
        // Libraries unlikely to change: org.w3c.do, org.objectweb.asm, org.xmlpull, etc.
        if (internal.startsWith("com/android/") &&
            // We have a few clashes with this namespace which are not part of
            // the API surface; remove these
            !internal.startsWith("com/android/tools/lint/checks/studio/") &&
            !internal.startsWith("com/android/tools/lint/checks/infrastructure/")
        ) {
            return true
        }

        // Imported APIs
        return internal.startsWith("org/jetbrains/uast") ||
            internal.startsWith("org/jetbrains/kotlin/psi") ||
            internal.startsWith("org/jetbrains/kotlin/asJava") ||
            internal.startsWith("com/intellij/psi")
    }

    /**
     * Returns true if the given lintJar does not contain any classes
     * referencing lint APIs that are not valid in the current class
     * loader
     */
    fun isCompatible(lintJar: File): Boolean {
        // Scans through the bytecode for all the classes in lint.jar, and
        // checks any class, method or field reference accessing the Lint API
        // and makes sure that API is found in the bytecode
        JarFile(lintJar).use { jar ->
            jar.entries().also { entries ->
                while (entries.hasMoreElements()) {
                    (entries.nextElement() as java.util.jar.JarEntry).also { entry ->
                        if (entry.name.endsWith(DOT_CLASS) && !entry.isDirectory) {
                            jar.getInputStream(entry).use { stream ->
                                val bytes = ByteStreams.toByteArray(stream)
                                val reader = ClassReader(bytes)
                                reader.accept(this, SKIP_DEBUG or SKIP_FRAMES)
                                if (incompatibleReference != null) {
                                    return false
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /** Returns a message describing the incompatibility */
    fun describeFirstIncompatibleReference(): String {
        val reference = incompatibleReference ?: return "Compatible"
        val index = reference.indexOf('#')
        if (index == -1) {
            return Type.getObjectType(reference).className.replace('$', '.')
        }
        val className = Type.getObjectType(reference.substring(0, index)).className.replace('$', '.')
        val paren = reference.indexOf('(')
        if (paren == -1) {
            // Field
            return className + "#" + reference.substring(index + 1)
        }

        // Method
        val sb = StringBuilder(className).append(": ")
        val descriptor = reference.substring(paren)
        val name = reference.subSequence(index + 1, paren)
        val arguments = Type.getArgumentTypes(descriptor)
        if (name == CONSTRUCTOR_NAME) {
            sb.append(className.substring(className.lastIndexOf('.') + 1))
        } else {
            val returnType = Type.getReturnType(descriptor).className
            sb.append(returnType).append(' ')
            sb.append(name)
        }
        sb.append('(')
        sb.append(arguments.joinToString(",") { it.className })
        sb.append(')')
        return sb.toString()
    }

    /** The internal name of the invalid reference. */
    private var incompatibleReference: String? = null

    private val methodVisitor = object : MethodVisitor(ASM7) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            checkMethod(owner, name, descriptor)
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String?) {
            checkField(owner, name)
            super.visitFieldInsn(opcode, owner, name, descriptor)
        }
    }

    /**
     * Checks that the class for the given [internal] name is valid:
     * relevant and exists in the current class node. If not, this
     * method sets the [incompatibleReference] property.
     */
    private fun checkClass(internal: String) {
        if (isRelevantApi(internal)) {
            try {
                // Ignoring return value: what we're looking for here is a throw
                getClass(internal)
            } catch (e: Throwable) {
                incompatibleReference = internal
            }
        }
    }

    /**
     * Count number of visited elements: for debugging statistics only.
     */
    var apiCount = 0
        private set

    /**
     * Checks that the method for the given containing class [owner]
     * and method [name] is valid: relevant and exists in the current
     * class node. If not, this method sets the [incompatibleReference]
     * property.
     */
    private fun checkMethod(owner: String, name: String, descriptor: String) {
        if (isRelevantApi(owner)) {
            try {
                apiCount++
                // Ignoring return value: what we're looking for here is a throw
                getMethod(owner, name, descriptor)
            } catch (e: Throwable) {
                incompatibleReference = "$owner#$name$descriptor"
            }
        }
    }

    /**
     * Checks that the field for the given containing class [owner]
     * and field [name] is valid: relevant and exists in the current
     * class node. If not, this method sets the [incompatibleReference]
     * property.
     */
    private fun checkField(owner: String, name: String) {
        if (isRelevantApi(owner)) {
            try {
                apiCount++
                // Ignoring return value: what we're looking for here is a throw
                getField(owner, name)
            } catch (e: Throwable) {
                incompatibleReference = "$owner#$name"
            }
        }
    }

    /** Loads the class of the given [internal] name */
    private fun getClass(internal: String): Class<*> {
        apiCount++
        val className = Type.getObjectType(internal).className
        return Class.forName(className)
    }

    /**
     * Returns the [Method] or [Constructor] referenced by the given
     * containing class internal name, [owner], the method name [name],
     * and internal method descriptor. Will throw an exception if the
     * method or constructor does not exist.
     */
    private fun getMethod(owner: String, name: String, descriptor: String): Executable {
        // Initially I thought I should cache this but it turns out
        // this is already pretty fast -- all 137 lint.jar files on
        // maven.google.com as of today with combined size 2.8M is
        // analyzed in around one second -- e.g. 7.5ms per jar.
        val clz = getClass(owner)

        val argumentTypes = Type.getArgumentTypes(descriptor)
            .map { type -> type.toTypeClass() }
            .toTypedArray()

        return if (name == CONSTRUCTOR_NAME) {
            try {
                clz.getDeclaredConstructor(*argumentTypes)
            } catch (e: Throwable) {
                clz.getConstructor(*argumentTypes)
            }
        } else {
            try {
                clz.getDeclaredMethod(name, *argumentTypes)
            } catch (e: Throwable) {
                clz.getMethod(name, *argumentTypes)
            }
        }
    }

    /**
     * Returns the [Field] referenced by the given containing class
     * internal name, [owner], and the field name. Will throw an
     * exception if the field does not exist.
     */
    private fun getField(owner: String, name: String): Field {
        val clz = getClass(owner)
        return try {
            clz.getDeclaredField(name)
        } catch (e: Throwable) {
            clz.getField(name)
        }
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        superName?.let { checkClass(it) }
        interfaces?.let { it.forEach { internal -> checkClass(internal) } }
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        return methodVisitor
    }
}

/**
 * Given an ASM type compute the corresponding java.lang.Class. I really
 * thought ASM would have this functionality, and perhaps it does, but I
 * could not find it.
 */
private fun Type.toTypeClass(): Class<out Any> {
    return when (descriptor) {
        "Z" -> java.lang.Boolean.TYPE
        "B" -> Byte.TYPE
        "C" -> Character.TYPE
        "S" -> Short.TYPE
        "I" -> Integer.TYPE
        "J" -> Long.TYPE
        "F" -> Float.TYPE
        "D" -> Double.TYPE
        "V" -> Void.TYPE
        else -> {
            when {
                descriptor.startsWith("L") -> Class.forName(className)
                descriptor.startsWith("[") ->
                    java.lang.reflect.Array.newInstance(elementType.toTypeClass(), 0)::class.java
                else -> error("Unexpected internal type $descriptor")
            }
        }
    }
}
