/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.artifact.impl

import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean

@Ignore
abstract class AbstractSingleArtifactTest<T: FileSystemLocation>(
    private val propertyAllocator: (ObjectFactory) -> Property<T>,
    private val valueAllocator: (DirectoryProperty, String) -> Provider<T>,
    private val taskAllocator: (tasks: TaskContainer, name: String) -> TaskProvider<out ProducerTask<T>>) {

    @Rule
    @JvmField val tmpDir: TemporaryFolder = TemporaryFolder()
    lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()
    }

    private fun allocateProperty() = SinglePropertyAdapter(propertyAllocator(project.objects))
    private fun allocateValue(name: String) = valueAllocator(project.layout.buildDirectory, name)
    private fun allocateTask(name: String) = taskAllocator(project.tasks, name)

    @Test
    fun testSet() {
        val artifact= SingleArtifactContainer {
            allocateProperty()
        }

        val initialized= AtomicBoolean(false)
        val value = allocateValue("firstProduced")
        val producer = taskAllocator(project.tasks, "firstProducer")
        producer.configure {
            initialized.set(true)
            it.getOutputFile().set(value)
        }

        artifact.replace(producer.flatMap { it.getOutputFile() })
        assertThat(initialized.get()).isFalse()

        assertThat(artifact.get().isPresent).isTrue()
        assertThat(artifact.get().get().asFile.name).isEqualTo(value.get().asFile.name)
        assertThat(initialized.get()).isTrue()
    }

    @Test
    fun testFileReset() {
        val artifact= SingleArtifactContainer {
            allocateProperty()
        }

        val firstInitialized= AtomicBoolean(false)
        val secondInitialized= AtomicBoolean(false)
        val outputFile = allocateValue("firstProduced")
        val firstProducer = allocateTask("firstProducer")
        firstProducer.configure {
            firstInitialized.set(true)
            it.getOutputFile().set(outputFile)
        }
        artifact.replace(firstProducer.flatMap { it.getOutputFile() })
        assertThat(firstInitialized.get()).isFalse()

        val outputFile2 = allocateValue("secondProduced")
        val secondProducer= allocateTask("secondProducer")
        secondProducer.configure {
            secondInitialized.set(true)
            it.getOutputFile().set(outputFile2)
        }

        artifact.replace(secondProducer.flatMap { it.getOutputFile() })
        assertThat(secondInitialized.get()).isFalse()

        assertThat(artifact.get().isPresent).isTrue()
        assertThat(artifact.get().get().asFile.name).isEqualTo("secondProduced")
        assertThat(firstInitialized.get()).isFalse()
        assertThat(secondInitialized.get()).isTrue()
    }

    @Test
    fun testGetCurrent() {
        val artifact= SingleArtifactContainer {
            allocateProperty()
        }

        val firstInitialized= AtomicBoolean(false)
        val secondInitialized= AtomicBoolean(false)
        val outputFile = allocateValue("firstProducer")
        val firstProducer = allocateTask("firstProducer")
        firstProducer.configure {
            firstInitialized.set(true)
            it.getOutputFile().set(outputFile)
        }

        artifact.replace(firstProducer.flatMap { it.getOutputFile() })
        val currentProducer = artifact.getCurrent()
        assertThat(firstInitialized.get()).isFalse()

        val outputFile2 = allocateValue("secondProducer")
        val secondProducer = allocateTask("secondProducer")
        secondProducer.configure {
            secondInitialized.set(true)
            it.getOutputFile().set(outputFile2)
        }

        artifact.replace(secondProducer.flatMap { it.getOutputFile() })
        val finalProducer= artifact.get()

        assertThat(currentProducer.get().asFile.name).isEqualTo("firstProducer")
        assertThat(finalProducer.get().asFile.name).isEqualTo("secondProducer")
    }

    @Test(expected = RuntimeException::class)
    fun testChangesDisallowed() {
        val artifact= SingleArtifactContainer { allocateProperty() }

        val outputFile = allocateValue("finalProduced")
        val finalProducer= allocateTask("finalProducer")
        finalProducer.configure {
            it.getOutputFile().set(outputFile)
        }
        artifact.replace(finalProducer.flatMap { it.getOutputFile() })
        artifact.disallowChanges()

        // now try to replace it.
        val outputFile2 = allocateValue("secondProducer")
        val otherProducer= allocateTask("otherProducer")
        otherProducer.configure {
            it.getOutputFile().set(outputFile2)
        }

        artifact.replace(otherProducer.flatMap { it.getOutputFile() })
    }

    @Test
    fun testAgpProducer() {
        val artifact= SingleArtifactContainer { allocateProperty() }
        abstract class ConsumerTask<T>: DefaultTask() {
            @InputFile abstract fun getInputFile(): Property<T>
        }
        @Suppress("Unchecked_cast")
        val consumerTaskProvider =
            project.tasks.register("consumerTask", ConsumerTask::class.java) as TaskProvider<ConsumerTask<T>>

        consumerTaskProvider.configure {
            it.getInputFile().set(artifact.get())
        }

        // now sets the initial provider which will happen after we run the variant API hooks.
        val outputFile2 = allocateValue("agpProducer")
        val otherProducer= allocateTask("agpProducer")
        otherProducer.configure {
            it.getOutputFile().set(outputFile2)
        }
        artifact.setInitialProvider(otherProducer.flatMap { it.getOutputFile() })

        // verify the chaining is correct.
        assertThat(artifact.get().isPresent).isTrue()
        assertThat(artifact.get().get()).isEqualTo(artifact.current.get().get())
    }

    @Test
    fun testAgpProducerReplaced() {
        val artifact= SingleArtifactContainer { allocateProperty() }

        abstract class ConsumerTask<T>: DefaultTask() {
            @InputFile abstract fun getInputFile(): Property<T>
        }
        @Suppress("Unchecked_cast")
        val consumerTaskProvider =
            project.tasks.register("consumerTask", ConsumerTask::class.java) as TaskProvider<ConsumerTask<T>>

        consumerTaskProvider.configure {
            it.getInputFile().set(artifact.get())
        }

        // now replace the "current" provider which is meant to replace the agp provider.
        val firstInitialized= AtomicBoolean(false)
        val outputFile = allocateValue("firstProduced")
        val firstProducer = allocateTask("firstProducer")
        firstProducer.configure {
            firstInitialized.set(true)
            it.getOutputFile().set(outputFile)
        }
        artifact.replace(firstProducer.flatMap { it.getOutputFile() })

        // now sets the initial provider which will happen after we run the variant API hooks.
        // obviously, in AGP, we might not even do that if we realize that the artifact is being
        // provided by another task.
        val outputFile2 = allocateValue("agpProducer")
        val otherProducer= allocateTask("agpProducer")
        otherProducer.configure {
            it.getOutputFile().set(outputFile2)
        }
        artifact.setInitialProvider(otherProducer.flatMap { it.getOutputFile() })

        // verify the chaining is correct.
        assertThat(artifact.get().isPresent).isTrue()
        assertThat(artifact.get().get()).isNotEqualTo(artifact.current.get())
        assertThat(artifact.get().get().asFile.name).isEqualTo("firstProduced")
    }

    abstract class ProducerTask<T: FileSystemLocation>: DefaultTask() {
        @OutputFile
        abstract fun getOutputFile(): Property<T>
    }
}