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

package com.android.build.gradle.internal.instrumentation

import com.android.SdkConstants.DOT_CLASS
import com.android.SdkConstants.DOT_JAR
import com.google.common.annotations.VisibleForTesting
import com.google.common.io.ByteStreams
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_CODE
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.annotation.concurrent.GuardedBy

/**
 * Contains data about the hierarchy of classes which includes, the superclass and the interfaces of
 * each class.
 *
 * Each class is represented via its internal name.
 */
class ClassesHierarchyData(private val asmApiVersion: Int) {

    @GuardedBy("lock")
    private val sourceDirs: MutableSet<File> = mutableSetOf()

    @GuardedBy("lock")
    private val sourceJars: MutableSet<File> = mutableSetOf()
    private val loadedClassesData: MutableMap<String, ClassData> = mutableMapOf()

    private val lock = Any()

    fun addSources(files: Set<File>) {
        addSources(*files.toTypedArray())
    }

    fun addSources(vararg files: File) {
        synchronized(lock) {
            files.forEach {
                if (it.name.endsWith(DOT_JAR)) {
                    sourceJars.add(it)
                } else {
                    sourceDirs.add(it)
                }
            }
        }
    }

    @GuardedBy("lock")
    @VisibleForTesting
    fun addClass(
        className: String,
        annotations: List<String>,
        superClass: String?,
        interfaces: List<String>
    ) {
        synchronized(lock) {
            loadedClassesData[className] = ClassData(annotations, superClass, interfaces)
        }
    }

    private fun addClass(classInputStream: InputStream): ClassData {
        var superclassName: String? = null
        val annotationsList = mutableListOf<String>()
        val interfacesList = mutableListOf<String>()
        classInputStream.use { inputStream ->
            val classReader = ClassReader(ByteStreams.toByteArray(inputStream))
            classReader.accept(object : ClassVisitor(asmApiVersion) {

                override fun visitAnnotation(
                    descriptor: String?,
                    visible: Boolean
                ): AnnotationVisitor? {
                    if (descriptor != "Lkotlin/Metadata;") {
                        annotationsList.add(descriptor!!.substring(1, descriptor.length - 1))
                    }
                    return null
                }

                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    superclassName = superName
                    interfacesList.addAll(interfaces!!)
                }
            }, SKIP_CODE or SKIP_FRAMES or SKIP_DEBUG)
        }
        return ClassData(annotationsList, superclassName, interfacesList)
    }

    @GuardedBy("lock")
    private fun loadClassData(className: String): ClassData {
        synchronized(lock) {
            return loadedClassesData.computeIfAbsent(className, this::computeClassData)
        }
    }

    private fun computeClassData(className: String): ClassData {
        val classFileName = className + DOT_CLASS
        sourceJars.forEach { jar ->
            ZipFile(jar).use { jarFile ->
                jarFile.getEntry(classFileName)?.let { entry ->
                    return addClass(jarFile.getInputStream(entry))
                }
            }
        }

        sourceDirs.forEach { dir ->
            val classFile = dir.resolve(classFileName)
            if (classFile.exists()) {
                return addClass(classFile.inputStream().buffered())
            }
        }

        throw RuntimeException("Unable to find classes hierarchy for class $className")
    }

    fun getAnnotations(className: String): List<String> {
        return loadClassData(className).annotations.map { it.replace('/', '.') }
    }

    /**
     * Returns a list of the superclasses of a certain class in the order of the class hierarchy.
     *
     * Example:
     *
     * A extends B
     * B extends C
     *
     * when invoking getAllSuperClasses(A)
     *
     * the method will return {B, C, java.lang.Object}
     */
    fun getAllSuperClasses(className: String): List<String> {
        return doGetAllSuperClasses(className).reversed().map { it.replace('/', '.') }
    }

    /**
     * Returns a list of the interfaces of a certain class sorted by name.
     *
     * Example:
     *
     * A implements B
     * B implements C
     *
     * when invoking getAllInterfaces(A)
     *
     * the method will return {B, C}
     */
    fun getAllInterfaces(className: String): List<String> {
        return doGetAllInterfaces(className).sorted().map { it.replace('/', '.') }
    }

    private fun doGetAllSuperClasses(className: String): MutableList<String> {
        val classData = loadClassData(className)
        if (classData.superClass == null) {
            return mutableListOf()
        }
        return doGetAllSuperClasses(classData.superClass).apply { add(classData.superClass) }
    }

    private fun doGetAllInterfaces(className: String): MutableSet<String> {
        val classData = loadClassData(className)
        return mutableSetOf<String>().apply {
            if (classData.superClass != null) {
                addAll(doGetAllInterfaces(classData.superClass))
            }
            classData.interfaces.forEach { interfaceClass ->
                if (add(interfaceClass)) {
                    addAll(doGetAllInterfaces(interfaceClass))
                }
            }
        }
    }

    private data class ClassData(
        val annotations: List<String>,
        val superClass: String?,
        val interfaces: List<String>
    )
}
