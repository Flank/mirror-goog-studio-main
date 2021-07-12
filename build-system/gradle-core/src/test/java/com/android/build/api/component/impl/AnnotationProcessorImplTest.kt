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

package com.android.build.api.component.impl

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.api.AnnotationProcessorOptions
import com.android.build.gradle.internal.fixtures.FakeListProperty
import com.android.build.gradle.internal.fixtures.FakeMapProperty
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.google.common.truth.Truth
import org.gradle.process.CommandLineArgumentProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AnnotationProcessorImplTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var annotationProcessorOptions: AnnotationProcessorOptions

    @Mock
    lateinit var internalServices: VariantPropertiesApiServices

    private fun initMocks(
        classNames: List<String>,
        arguments: Map<String, String>,
        providers: List<CommandLineArgumentProvider>) {
        `when`(annotationProcessorOptions.classNames).thenReturn(classNames)
        `when`(internalServices.listPropertyOf(String::class.java, classNames))
            .thenReturn(FakeListProperty(classNames))
        `when`(annotationProcessorOptions.arguments).thenReturn(arguments)
        `when`(internalServices.mapPropertyOf(String::class.java, String::class.java, arguments))
            .thenReturn(FakeMapProperty(arguments))
        `when`(annotationProcessorOptions.compilerArgumentProviders).thenReturn(providers)
    }

    @Test
    fun testFinalListOfClassNames_empty() {
        initMocks(listOf(), mapOf(), listOf())
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_with_random_processors() {
        initMocks(listOf("com.foo.RandomProcessor"), mapOf(), listOf())
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains("com.foo.RandomProcessor")
    }

    @Test
    fun testFinalListOfClassNames_with_random_processors_including_databinding() {
        initMocks(
            listOf("com.foo.RandomProcessor", DataBindingBuilder.PROCESSOR_NAME),
            mapOf(),
            listOf()
        )
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains("com.foo.RandomProcessor")
    }

    @Test
    fun testFinalListOfClassNames_withArguments() {
        initMocks(listOf(), mapOf("-processor" to "foo.bar.SomeProcessor"), listOf())
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArguments_including_databinding() {
        initMocks(listOf(),
            mapOf("-processor" to "foo.bar.SomeProcessor:${DataBindingBuilder.PROCESSOR_NAME}"),
            listOf()
        )
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        // since it is present in arguments, it should not be in the final class names.
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArgumentProviders() {
        val argumentProvider =
            CommandLineArgumentProvider { listOf("-processor", "com.foo.SomeProcessor") }
        initMocks(listOf(), mapOf(), listOf(argumentProvider))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArgumentProviders_including_databinding() {
        val argumentProvider =
            CommandLineArgumentProvider {
                listOf("-processor", "com.foo.SomeProcessor:${DataBindingBuilder.PROCESSOR_NAME}") }
        initMocks(listOf(), mapOf(), listOf(argumentProvider))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        // since it is present in argumentProviders, it should not be in the final class names
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }
}
