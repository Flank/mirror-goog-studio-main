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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputFiles
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
import kotlin.test.fail

@Ignore
abstract class AbstractMultipleArtifactTest<T: FileSystemLocation>(
    private val propertyAllocator: (ObjectFactory) -> ListProperty<T>,
    private val valueAllocator: (DirectoryProperty, String) -> Provider<T>,
    private val taskAllocator: (tasks: TaskContainer, name: String) -> TaskProvider<out AbstractSingleArtifactTest.ProducerTask<T>>
) {

    @Rule
    @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()
    lateinit var project: Project

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(tmpDir.newFolder()).build()
    }

    private fun allocateProperty() =  MultiplePropertyAdapter(propertyAllocator(project.objects))
    private fun allocateValue(name: String) = valueAllocator(project.layout.buildDirectory, name)
    private fun allocateTask(name: String) = taskAllocator(project.tasks, name)

    @Test
    fun testSet() {
        val artifact = MultipleArtifactContainer { allocateProperty() }

        val initialized = AtomicBoolean(false)
        val value = allocateValue("firstProduced")
        val producer = taskAllocator(project.tasks, "firstProducer")
        producer.configure {
            initialized.set(true)
            it.getOutputFile().set(value)
        }

        artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
        assertThat(initialized.get()).isFalse()

        assertValues(artifact.getCurrent(), listOf(value))
        assertValues(artifact.get(), listOf(value))
        assertThat(initialized.get()).isTrue()
    }

    @Test
    fun testMultipleSet() {
        val artifact = MultipleArtifactContainer { allocateProperty() }

        val listOfValues= mutableListOf<Provider<T>>()
        for (i in 1..3) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
            listOfValues.add(value)
        }
        assertValues(artifact.get(), listOfValues)
    }

    @Test(expected = RuntimeException::class)
    fun testChangesDisallowed() {
        val artifact = MultipleArtifactContainer { allocateProperty() }

        val listOfValues= mutableListOf<Provider<T>>()
        for (i in 1..3) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
            listOfValues.add(value)
        }
        artifact.disallowChanges()
        // try to add an extra producer, it should fail
        val value = allocateValue("extraProduced")
        val producer = taskAllocator(project.tasks, "extraProducer")
        producer.configure {
            it.getOutputFile().set(value)
        }
        artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
    }

    @Test
    fun testGetFinal() {
        val artifact = MultipleArtifactContainer { allocateProperty() }
        val final = artifact.get()
        assertThat(final.get()).isEmpty()

        // add some producers.
        for (i in 1..3) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
        }
        artifact.disallowChanges()
        assertThat(final.get().size).isEqualTo(3)

        // check that the collection cannot be altered.
        if (final is ListProperty<*>) {
            try {
                @Suppress("Unchecked_cast")
                final.add(allocateValue("foo") as Provider<out Nothing>)
                fail("getCurrent() returned an unprotected collection")
            } catch(expected: Exception) {
                // expected
            }
        }
    }

    @Test
    fun testGetCurrent() {
        val artifact = MultipleArtifactContainer { allocateProperty() }

        // initial producer.
        for (i in 1..3) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
        }
        val currentValues = artifact.getCurrent()

        // add a few more producers.
        for (i in 4..5) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
        }
        assertThat(currentValues.get().size).isEqualTo(5)

        // check that the collection cannot be altered.
        if (currentValues is ListProperty<*>) {
            try {
                @Suppress("Unchecked_cast")
                currentValues.add(allocateValue("foo") as Provider<out Nothing>)
                fail("getCurrent() returned an unprotected collection")
            } catch(expected: Exception) {
                // expected
            }
        }
    }

    fun testReplace(multipleProducerAllocator:
        (TaskContainer, String) -> TaskProvider<out MultipleProducerTask<T>>) {

        val artifact = MultipleArtifactContainer { allocateProperty() }

        val listOfValues= mutableListOf<Provider<T>>()
        val initialProducerConfigured = AtomicBoolean(false)

        val producer= allocateCombiningProducers(
            multipleProducerAllocator,
            "initialProducer",
            initialProducerConfigured,
            listOfValues
        )
        artifact.setInitialProvider(producer.flatMap { it.getOutputFiles() })

        // and now replace all in by one task.
        val secondProducer= allocateCombiningProducers(
            multipleProducerAllocator,
            "secondProducer",
            values = listOfValues)
        artifact.replace(secondProducer.flatMap { it.getOutputFiles() })

        assertValues(artifact.get(), listOfValues)
        // make sure the initial provider is not configured since it's replaced.
        assertThat(initialProducerConfigured.get()).isFalse()
    }

    fun testAddAndReplace(multipleProducerAllocator:
        (TaskContainer, String) -> TaskProvider<out MultipleProducerTask<T>>) {

        val artifact = MultipleArtifactContainer { allocateProperty() }

        val initialProducerConfigured= AtomicBoolean(false)
        val initialProviders = mutableListOf<Provider<T>>()
        val producer = allocateCombiningProducers(
            multipleProducerAllocator,
            "initialProducer",
            initialProducerConfigured,
            initialProviders
        )

        artifact.setInitialProvider(producer.flatMap { it.getOutputFiles() })
        // test current
        var currentArtifactValues = artifact.getCurrent()
        assertThat(currentArtifactValues.get().size).isEqualTo(3)

        // add an element
        val addedValue = allocateValue("secondProduced")
        val addedProducer = taskAllocator(project.tasks, "addedProducer")
        addedProducer.configure {
            it.getOutputFile().set(addedValue)
            initialProviders.add(addedValue)
        }
        artifact.addInitialProvider(addedProducer.flatMap { it.getOutputFile() })

        // test current
        currentArtifactValues = artifact.getCurrent()
        // it should still be 3 since we have replaced all producers with "initialProducer"
        assertThat(currentArtifactValues.get().size).isEqualTo(4)
        assertThat(currentArtifactValues.get()[3].asFile.name).isEqualTo("secondProduced")

        // and now replace all these values in by one combining task.
        val replacingProducer= multipleProducerAllocator(project.tasks, "secondProducer")
        val replacingValue = allocateValue("replacement")
        replacingProducer.configure {
            it.getOutputFiles().add(replacingValue)
        }

        artifact.replace(replacingProducer.flatMap { it.getOutputFiles() })
        // from now on, only one provider remain, which is "replacingValue"

        // test current
        assertValues(artifact.getCurrent(), listOf(replacingValue))

        // test final.
        assertValues(artifact.get(), listOf(replacingValue))

        // assert that original current value has not been polluted by subsequent operations.
        assertValues(currentArtifactValues, initialProviders)
   }

    fun testTransform(multipleProducerAllocator:
        (TaskContainer, String) -> TaskProvider<out MultipleProducerTask<T>>) {

        val artifact = MultipleArtifactContainer { allocateProperty() }
        val initialProducersInitialized = AtomicBoolean(false)

        for (i in 1..3) {
            val value = allocateValue("firstProduced$i")
            val producer = taskAllocator(project.tasks, "firstProducer$i")
            producer.configure {
                it.getOutputFile().set(value)
                initialProducersInitialized.set(true)
            }
            artifact.addInitialProvider(producer.flatMap { it.getOutputFile() })
        }

        val value = allocateValue("transformed")
        val transformer = multipleProducerAllocator(project.tasks, "transformer")
        transformer.configure {
            it.getOutputFiles().add(value)
        }
        artifact.transform(transformer.flatMap { it.getOutputFiles() })

        assertValues(artifact.get(), listOf(value))
        // none of the initial providers should be involved.
        assertThat(initialProducersInitialized.get()).isFalse()
    }

    private fun allocateCombiningProducers(
        multipleAllocator: (TaskContainer, String) -> TaskProvider<out MultipleProducerTask<T>>,
        name: String,
        configuredRecorder: AtomicBoolean? = null,
        values: MutableList<Provider<T>>? = null
    ): TaskProvider<out MultipleProducerTask<T>>{

        val producer= multipleAllocator(project.tasks, name)

        for (i in 1..3) {
            val value = allocateValue("$name$i")
            producer.configure {
                it.getOutputFiles().add(value)
                values?.add(value)
            }
        }
        if (configuredRecorder!=null) {
            producer.configure {
                configuredRecorder.set(true)
            }
        }
        return producer
    }

    private fun assertValues(provider: Provider<List<T>>, values: List<Provider<T>>) {
        assertThat(provider.isPresent).isTrue()
        val listOfValues = provider.get()
        assertThat(listOfValues.size).isEqualTo(values.size)
        for (i in values.indices)  {
            assertThat(listOfValues[i].asFile.name).isEqualTo(values[i].get().asFile.name)
        }
    }

    abstract class MultipleProducerTask<T: FileSystemLocation>: DefaultTask() {
        @OutputFiles
        abstract fun getOutputFiles(): ListProperty<T>
    }
}