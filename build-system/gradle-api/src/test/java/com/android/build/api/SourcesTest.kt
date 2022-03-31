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

package com.android.build.api

import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class SourcesTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var sources: Sources

    @Test
    fun testJavaAll() {
        val javaSources = Mockito.mock(SourceDirectories.Flat::class.java)
        Mockito.`when`(sources.java).thenReturn(javaSources)
        sources.java?.all
    }

    @Test
    fun testJavaAddSource() {
        abstract class AddingTask: DefaultTask() {
            @get:OutputFiles
            abstract val output: DirectoryProperty
        }
        @Suppress("UNCHECKED_CAST")
        val taskProvider = Mockito.mock(TaskProvider::class.java) as TaskProvider<AddingTask>

        val javaSources = Mockito.mock(SourceDirectories.Flat::class.java)
        Mockito.`when`(sources.java).thenReturn(javaSources)

        sources.java?.addGeneratedSourceDirectory(taskProvider, AddingTask::output)
    }

    @Test
    fun testJavaAddDirectory() {
        val javaSources = Mockito.mock(SourceDirectories.Flat::class.java)
        Mockito.`when`(sources.java).thenReturn(javaSources)

        sources.java?.addStaticSourceDirectory("/path/to/directory")
    }

    @Test
    fun testResAll() {
        val resSources = Mockito.mock(SourceDirectories.Layered::class.java)
        Mockito.`when`(sources.res).thenReturn(resSources)
        sources.res?.all
    }

    @Test
    fun testResAddSource() {
        abstract class AddingTask: DefaultTask() {
            @get:OutputFiles
            abstract val output: DirectoryProperty
        }
        @Suppress("UNCHECKED_CAST")
        val taskProvider = Mockito.mock(TaskProvider::class.java) as TaskProvider<AddingTask>

        val resSources = Mockito.mock(SourceDirectories.Layered::class.java)
        Mockito.`when`(sources.res).thenReturn(resSources)

        sources.res?.addGeneratedSourceDirectory(taskProvider, AddingTask::output)
    }

    @Test
    fun testResAddDirectory() {
        val resSources = Mockito.mock(SourceDirectories.Layered::class.java)
        Mockito.`when`(sources.res).thenReturn(resSources)

        sources.res?.addStaticSourceDirectory("/path/to/directory")
    }
}
