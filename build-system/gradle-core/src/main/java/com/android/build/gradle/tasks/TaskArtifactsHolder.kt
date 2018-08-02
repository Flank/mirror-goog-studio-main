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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.isAccessible

class TaskArtifactsHolder<T: Task>(val artifacts: BuildArtifactsHolder) {

    private val inputs = mutableListOf<InputFilesInjectPoint<T>>()
    private val outputs = mutableListOf<OutputDirectoryInjectPoint<T>>()

    /**
     * Wires all input and output fields of a task using BuildableArtifacts.
     *
     * All input or output fields must be annotated with an identification annotation. Example of
     * such annotation can be found at [InternalID]. An annotation identification must be annotated
     * with [IDProvider].
     *
     * All input fields will be injected with either the current or the final value of the buildable
     * artifact identified by the [IDProvider] annotated annotation.
     *
     * Automatically allocate Providers of [RegularFile] or [Directory] for output file and folders.
     *
     * Provide automatic checks like requesting a File buildable artifacts to be consumed or
     * produced as a directory
     *
     * @param configAction the configuration object pointing the task with inputs and outputs
     * definitions.
     * @param provider the task provider, to set dependencies. [TaskProvider.get] must not be
     * called.
     *
     */
    fun allocateArtifacts(configAction: AnnotationProcessingTaskCreationAction<T>) {

        configAction.type.methods.forEach {

            val inputFiles = it.findAnnotation<InputFiles>()

            if (inputFiles != null) {
                val inputId = findID(it)
                if (inputId != null) {
                    // find the corresponding property definition.
                    val propertyDefinition = findProperty<BuildableArtifact>(
                        configAction.type.kotlin,
                        it.name.substring(3).decapitalize()
                    ) ?: throw RuntimeException("Cannot find property for ${it.name}")

                    // check that field is the right type.
                    checkInputType(it)

                    inputs.add(
                        InputFilesInjectPoint(
                            propertyDefinition,
                            artifacts.getFinalArtifactFiles(inputId)
                        )
                    )
                }
            }

            val outputDirectory = it.findAnnotation<OutputDirectory>()
            val replace = it.findAnnotation<Replace>()

            if (outputDirectory!=null && replace!=null) {
                val outputId = findID(it)
                if (outputId != null) {
                    val propertyDefinition = findProperty<Provider<Directory>>(
                        configAction.type.kotlin,
                        it.name.substring(3).decapitalize()
                    ) ?: throw RuntimeException("Cannot find property for ${it.name}")

                    if (outputId.kind() != ArtifactType.Kind.DIRECTORY) {
                        throw RuntimeException(
                            "Task: ${it.declaringClass.name}\n\t" +
                                    "Method: ${it.toGenericString()}\n\t" +
                                    "annotated with $outputDirectory with ArtifactID \"${outputId.name()}\"\n\t" +
                                    "which kind is set to ${outputId.kind()}")
                    }

                    checkoutOutputType(it, outputDirectory, Directory::class)

                    outputs.add(
                        OutputDirectoryInjectPoint(
                            propertyDefinition,
                            artifacts.appendDirectory(
                                outputId,
                                configAction.name,
                                replace.out
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Performs all possible checks on an input field.
     */
    private fun checkInputType(annotatedElement: Method) {
        if (annotatedElement.returnType.typeName != BuildableArtifact::class.qualifiedName) {
            throw RuntimeException("Annotated method does not return a BuildableArtifact")
        }
    }

    /**
     * Perform all possible checks on an output field.
     */
    private fun checkoutOutputType(annotatedElement: Method, annotation: Any, expectedType: KClass<*>) {
        val returnTypeName = annotatedElement.returnType.typeName
        if (returnTypeName != Provider::class.qualifiedName) {
            throw RuntimeException(
                "Task: ${annotatedElement.declaringClass.name}\n\t" +
                        "Method: ${annotatedElement.toGenericString()}\n\t" +
                        "annotated with $annotation is expected to return a Provider<${expectedType.simpleName}> but instead returns $returnTypeName")
        }
        val genericReturnType = annotatedElement.genericReturnType
        if (genericReturnType is ParameterizedType) {
            val typeParameters = genericReturnType.actualTypeArguments
            if (typeParameters.size != 1) {
                throw RuntimeException("No parameterized type for Provider<> specified")
            }
            if (typeParameters[0].typeName != expectedType.qualifiedName) {
                throw RuntimeException(
                    "Task: ${annotatedElement.declaringClass.name}\n\t" +
                            "Method: ${annotatedElement.toGenericString()}\n\t" +
                            "annotated with $annotation is expected to return a Provider<${expectedType.simpleName}>\n\t" +
                            "but instead returns Provider<${typeParameters[0].typeName}>")
            }
        }
    }

    private inline fun <reified T : Annotation?> Method.findAnnotation(): T? {
        return this.getAnnotation(T::class.java)
    }

    /**
     * Once the [Task] has been created, transfer all the BuildableArtifacts into the task's
     * input fields and the Provider<> into the task's output fields.
     */
    fun transfer(task: T) {
        inputs.forEach { it.inject(task) }
        outputs.forEach { it.inject(task) }
    }

    private fun <R> findProperty(kclass: KClass<*>, propertyName: String): KMutableProperty1<Any, R>? {
        val prop = kclass.memberProperties.find { it.name == propertyName }
        if (prop!=null) {
            return prop as KMutableProperty1<Any, R>
        }
        kclass.superclasses.forEach {
            val superTypeProp = findProperty<R>(it, propertyName)
            if (superTypeProp != null) return superTypeProp
        }
        return null
    }

    /**
     * Find the annotation that is itself annotated with @ProviderID and extract the ArtifactType
     */
    private fun findID(annotatedElement: AnnotatedElement): ArtifactType? {
        annotatedElement.annotations.forEach{
            val idProvider = it.annotationClass.findAnnotation<IDProvider>()
            if (idProvider != null) {
                it.annotationClass.java.methods[0].invoke(it)
                val idProviderMethod = it.annotationClass.java.methods.find { method ->
                    method.name == idProvider.fieldName
                }
                if (idProviderMethod != null) {
                    val id = idProviderMethod.invoke(it)
                    if (id is ArtifactType) {
                        return id
                    } else {
                        throw RuntimeException(
                            "$it is annotated with @IdProvider," +
                                    " and the target method is $idProviderMethod, " +
                                    "yet $id is not an instance of ArtifactType")
                    }
                } else {
                    throw RuntimeException("Cannot find property ${idProvider.fieldName} " +
                            "on annotation type ${it.annotationClass}")
                }
            }
        }
        return null
    }

    private interface InjectionPoint<in T: Task> {
        fun inject(task: T)
    }

    private data class InputFilesInjectPoint<in T: Task>(
        val injectionPoint: KMutableProperty1<Any, BuildableArtifact>,
        val buildableArtifact: BuildableArtifact): InjectionPoint<T> {

        override fun inject(task: T) {
            injectionPoint.setter.isAccessible = true
            injectionPoint.setter.call(task, buildableArtifact)        }
    }

    private data class OutputDirectoryInjectPoint<in T: Task>(
        val injectionPoint: KMutableProperty1<Any, Provider<Directory>>,
        val output: Provider<Directory>): InjectionPoint<T> {

        override fun inject(task: T) {
            injectionPoint.setter.isAccessible = true
            injectionPoint.setter.call(task, output)
        }
    }
}