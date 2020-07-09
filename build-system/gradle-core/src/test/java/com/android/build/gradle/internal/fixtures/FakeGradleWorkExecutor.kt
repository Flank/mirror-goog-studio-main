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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.ProcessWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerConfiguration
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import org.gradle.workers.WorkerSpec
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.net.URLClassLoader

/**
 * Fake implementation of [WorkerExecutor]. [ObjectFactory] is used to instantiate parameters,
 * while [tmpDir] is used to output generated decorated classes.
 */
class FakeGradleWorkExecutor(
    objectFactory: ObjectFactory,
    tmpDir: File,
    injectableService: List<FakeInjectableService> = emptyList()
) : WorkerExecutor {

    private val workQueue =
        FakeGradleWorkQueue(objectFactory, tmpDir.resolve("generatedClasses"), injectableService)

    override fun submit(
        aClass: Class<out Runnable?>,
        action: Action<in WorkerConfiguration?>
    ) {
        TODO()
    }

    override fun noIsolation(): WorkQueue {
        return workQueue
    }

    override fun classLoaderIsolation(): WorkQueue {
        TODO()
    }

    override fun processIsolation(): WorkQueue {
        TODO()
    }

    override fun noIsolation(action: Action<in WorkerSpec?>): WorkQueue {
        TODO()
    }

    override fun classLoaderIsolation(action: Action<in ClassLoaderWorkerSpec?>): WorkQueue {
        TODO()
    }

    override fun processIsolation(action: Action<in ProcessWorkerSpec?>): WorkQueue {
        TODO()
    }

    override fun await() {
        // do nothing as we execute all actions on submit
    }
}

class FakeInjectableService(
    val methodReference: Method,
    val implementation: Any
)

/** Runs workers actions by directly instantiating worker actions and worker action parameters. */
class FakeGradleWorkQueue(
    private val objectFactory: ObjectFactory,
    private val generatedClassesOutput: File,
    private val injectableService: List<FakeInjectableService>
) : WorkQueue {
    @Suppress("UNCHECKED_CAST")
    override fun <T : WorkParameters> submit(
        aClass: Class<out WorkAction<T>>,
        action: Action<in T>
    ) {
        val parameterTypeName =
            (aClass.genericSuperclass as ParameterizedType).actualTypeArguments[0].typeName

        val workerActionName = aClass.name.replace(".", "/")
        val bytes = aClass.classLoader.getResourceAsStream("$workerActionName.class")
            .use { it!!.readBytes() }

        val reader = ClassReader(bytes)
        val cw = ClassWriter(0)
        reader.accept(WorkerActionDecorator(cw, parameterTypeName, injectableService), 0)

        generatedClassesOutput.resolve("$workerActionName$CLASS_SUFFIX.class").also {
            it.parentFile.mkdirs()
            it.writeBytes(cw.toByteArray())
        }

        URLClassLoader(
            arrayOf(generatedClassesOutput.toURI().toURL()),
            aClass.classLoader
        ).use { classloader ->
            // initialize and configure parameters
            val parametersInstance =
                objectFactory.newInstance(this::class.java.classLoader.loadClass(parameterTypeName)) as T
            action.execute(parametersInstance)

            // create and run worker action
            val loadClass: Class<*> = classloader.loadClass(aClass.name + CLASS_SUFFIX)
            val allConstructorArgs =
                (listOf(parametersInstance) + injectableService.map { it.implementation }).toTypedArray()
            val newInstance =
                loadClass.constructors.single().newInstance(*allConstructorArgs) as WorkAction<T>
            (newInstance as WorkAction<*>).execute()
        }
    }

    @Throws(WorkerExecutionException::class)
    override fun await() {
        // do nothing as we execute all actions on submit
    }
}

/**
 * Generates decorated class for worker action. The generated class does not have abstract
 * [WorkAction.getParameters] method, and instead it has a constructor which accepts
 * [parameterDescriptor] as argument.
 */
class WorkerActionDecorator(
    classWriter: ClassWriter,
    paramsType: String,
    private val injectableService: List<FakeInjectableService>
) :
    ClassVisitor(Opcodes.ASM7, classWriter) {

    private val parameterDescriptor = binaryToDescriptor(paramsType)

    lateinit var generatedClassName: String

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        generatedClassName = name + CLASS_SUFFIX

        val fieldAndDescriptors = mutableListOf<Pair<String, String>>()
        synthesizeFieldAndMethod("getParameters", WorkParameters::class.java.name).also {
            fieldAndDescriptors.add(it)
        }
        injectableService.forEach {
            fieldAndDescriptors.add(
                synthesizeFieldAndMethod(
                    it.methodReference.name,
                    it.methodReference.returnType.name
                )
            )
        }

        val constructorArgs = fieldAndDescriptors.joinToString(separator = "") { it.second }

        // add new constructor for parameters and all injectable services
        super.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            "($constructorArgs)V",
            null,
            null
        ).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false)

            fieldAndDescriptors.forEachIndexed { index, fieldAndDescriptor ->
                visitVarInsn(Opcodes.ALOAD, 0) // load this
                visitVarInsn(Opcodes.ALOAD, (index + 1)) // load constructor argument
                visitFieldInsn(
                    Opcodes.PUTFIELD,
                    generatedClassName,
                    fieldAndDescriptor.first,
                    fieldAndDescriptor.second
                );
            }
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 1 + fieldAndDescriptors.size)
            visitEnd()
        }

        super.visit(
            version,
            access and Modifier.ABSTRACT.inv(),
            generatedClassName,
            signature,
            name,
            interfaces
        )
    }

    private fun synthesizeFieldAndMethod(
        methodName: String,
        returnValueType: String
    ): Pair<String, String> {
        val descriptor = binaryToDescriptor(returnValueType)
        val fieldName = methodName + "_field"
        super.visitField(Opcodes.ACC_PRIVATE, fieldName, descriptor, null, null)

        super.visitMethod(
            Opcodes.ACC_PUBLIC,
            methodName,
            "()$descriptor",
            null,
            null
        ).apply {
            visitVarInsn(Opcodes.ALOAD, 0)
            visitFieldInsn(
                Opcodes.GETFIELD,
                generatedClassName,
                fieldName,
                descriptor
            )
            visitInsn(Opcodes.ARETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        return Pair(fieldName, descriptor)
    }

    private fun binaryToDescriptor(binaryName: String): String =
        "L" + binaryName.replace(".", "/") + ";"

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        // do not add any other fields
        return null
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        // do not add any other methods to the generated class
        return null
    }
}

private const val CLASS_SUFFIX = "_WithParams"
