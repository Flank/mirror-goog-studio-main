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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.WorkItemParameters
import com.android.build.api.artifact.impl.ArtifactTransformationRequestImpl
import com.android.build.api.artifact.impl.OperationsImpl
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.truth.Truth
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Tests for TaskBasedOperationsImpl
 */
class TaskBasedOperationsImplTest {

    abstract class AsynchronousTask @Inject constructor(var workers: WorkerExecutor): VariantAwareTask, DefaultTask() {

        @get:InputFiles
        abstract val inputDir: DirectoryProperty
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @get:Internal
        lateinit var replacementRequest: ArtifactTransformationRequest

        abstract class Parameters: WorkItemParameters() {
            abstract val builtArtifact: Property<BuiltArtifact>
            abstract val outputFile: RegularFileProperty
            abstract val someInputToWorkerItem: Property<Int>

            override fun initProperties(builtArtifact: BuiltArtifact, outputLocation: Directory): File {
                this.builtArtifact.set(builtArtifact)
                outputFile.set(outputLocation.file(File(builtArtifact.outputFile).name))
                return outputFile.get().asFile
            }
        }

        abstract class WorkItem @Inject constructor(private val workItemParameters: Parameters): WorkAction<Parameters> {
            override fun getParameters(): Parameters {
                return workItemParameters
            }

            override fun execute() {
                Truth.assertThat(parameters.someInputToWorkerItem.get()).isAnyOf(1,2,3)
                parameters.outputFile.get().asFile.writeText(
                    parameters.builtArtifact.get().filters.joinToString { it.identifier })
            }
        }

        @TaskAction
        open fun taskAction() {
            taskAction(workers.noIsolation())
        }

        protected fun taskAction(workerQueue: WorkQueue): Supplier<BuiltArtifacts> {
            val counter = AtomicInteger()
            return replacementRequest.submit(
                workerQueue,
                Parameters::class.java,
                WorkItem::class.java
            ) {
                it.someInputToWorkerItem.set(counter.incrementAndGet())
            }
        }

        class CreationAction(val component: VariantPropertiesImpl): VariantTaskCreationAction<AsynchronousTask, VariantPropertiesImpl>(component) {
            override val name: String
                get() = component.computeTaskName("foo", "bar")
            override val type: Class<AsynchronousTask>
                get() = AsynchronousTask::class.java

            private lateinit var replacementRequest: ArtifactTransformationRequest

            override fun handleProvider(taskProvider: TaskProvider<out AsynchronousTask>) {
                super.handleProvider(taskProvider)
                replacementRequest = component.operations
                    .use(taskProvider)
                    .toRead(type = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST, at = AsynchronousTask::inputDir)
                    .andWrite(type = InternalArtifactType.MERGED_MANIFESTS, at = AsynchronousTask::outputDir)
            }

            override fun configure(task: AsynchronousTask) {
                task.replacementRequest = replacementRequest
            }
        }
    }

    @Rule
    @JvmField val tmpDir: TemporaryFolder = TemporaryFolder()
    private lateinit var project: Project
    private lateinit var operations: OperationsImpl
    private val taskInitialized = AtomicBoolean(false)
    private val component = Mockito.mock(VariantPropertiesImpl::class.java)

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()
        operations = OperationsImpl(project.objects,"debug", project.layout.buildDirectory)

        val inputFolder = tmpDir.newFolder("input")
        val inputFolderProperty = project.objects.directoryProperty().also { it.set(inputFolder) }
        createBuiltArtifacts(
            createBuiltArtifact(inputFolder, "file1", 12, "xhdpi"),
            createBuiltArtifact(inputFolder, "file2", 12, "xxhdpi"),
            createBuiltArtifact(inputFolder, "file3", 12, "xxxhdpi")
        ).save(inputFolderProperty.get())

        operations.getArtifactContainer(InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST)
            .setInitialProvider(inputFolderProperty)
        Mockito.`when`(component.operations).thenReturn(operations)
    }

    @Test
    fun asynchronousApiTest() {
        asynchronousTest(false, AsynchronousTask::class.java)
    }

    private fun asynchronousTest(waiting: Boolean, taskType: Class<out AsynchronousTask>) {

        val taskACreationAction = AsynchronousTask.CreationAction(component)
        val taskProvider= project.tasks.register("replace", taskType)
        taskACreationAction.handleProvider(taskProvider)

        val outputFolder = tmpDir.newFolder("output")

        taskProvider.configure {
            taskInitialized.set(true)
            taskACreationAction.apply {
                configure(it)
                it.outputDir.set(outputFolder)
            }
        }

        // force lookup of the produced artifact, this should force task initialization.
        val mergedManifests = operations.get(InternalArtifactType.MERGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        // create a fake WorkQueue, that will instantiate the parameters and work action and invoke
        // it synchronously.
        val workQueue = object: WorkQueue {

            override fun <T : WorkParameters?> submit(
                p0: Class<out WorkAction<T>>?,
                p1: Action<in T>?
            ) {
                val parameters =
                    project.objects.newInstance(AsynchronousTask.Parameters::class.java)
                p1?.execute(parameters as T)
                val workItemAction = project.objects.newInstance(p0, parameters)
                workItemAction.execute()
            }

            override fun await() {
                Assert.assertTrue("Task was not supposed to wait for results", waiting)
            }
        }

        // replace injected WorkerExecutor with dummy version.
        val workerExecutor = Mockito.mock(WorkerExecutor::class.java)
        Mockito.`when`(workerExecutor.noIsolation()).thenReturn(workQueue)
        val task = taskProvider.get()
        task.workers = workerExecutor
        task.taskAction()

        // force wrap up
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<*, AsynchronousTask>).wrapUp(task)

        checkOutputFolderCorrectness(outputFolder)
    }

    abstract class WaitingAsynchronousClass @Inject constructor(workers: WorkerExecutor): AsynchronousTask(workers) {

        @TaskAction
        override fun taskAction() {
            super.taskAction(workers.noIsolation()).get()
        }
    }

    @Test
    fun asynchronousWaitingTest() {
        asynchronousTest(true, WaitingAsynchronousClass::class.java)
    }

    abstract class SynchronousTask @Inject constructor(val workers: WorkerExecutor): VariantAwareTask, DefaultTask() {

        @get:InputFiles
        abstract val inputDir: DirectoryProperty
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @get:Internal
        lateinit var replacementRequest: ArtifactTransformationRequest

        @TaskAction
        fun execute() {
            replacementRequest.submit { builtArtifact ->
                outputDir.get().file(File(builtArtifact.outputFile).name).asFile.apply {
                    writeText(builtArtifact.filters.joinToString { it.identifier })
                }
            }
        }

        class CreationAction(val component: VariantPropertiesImpl): VariantTaskCreationAction<SynchronousTask, VariantPropertiesImpl>(component) {
            override val name: String
                get() = component.computeTaskName("foo", "bar")
            override val type: Class<SynchronousTask>
                get() = SynchronousTask::class.java

            private lateinit var replacementRequest: ArtifactTransformationRequest

            override fun handleProvider(taskProvider: TaskProvider<out SynchronousTask>) {
                super.handleProvider(taskProvider)
                replacementRequest = component.operations
                    .use(taskProvider)
                    .toRead(type = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST, at = SynchronousTask::inputDir)
                    .andWrite(type = InternalArtifactType.MERGED_MANIFESTS, at = SynchronousTask::outputDir)
            }

            override fun configure(task: SynchronousTask) {
                task.replacementRequest = replacementRequest
            }
        }
    }

    @Test
    fun synchronousApiTest() {

        val taskACreationAction = SynchronousTask.CreationAction(component)
        val taskProvider= project.tasks.register("replace", SynchronousTask::class.java)
        taskACreationAction.handleProvider(taskProvider)

        val outputFolder = tmpDir.newFolder("output")
        taskProvider.configure {
            taskInitialized.set(true)
            taskACreationAction.apply {
                configure(it)
                it.outputDir.set(outputFolder)
            }
        }
        val mergedManifests = operations.get(InternalArtifactType.MERGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        val task = taskProvider.get()
        task.execute()
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<*, SynchronousTask>).wrapUp(task)

        checkOutputFolderCorrectness(outputFolder)
    }

    private fun checkOutputFolderCorrectness(outputFolder: File) {
        Truth.assertThat(outputFolder.listFiles()).hasLength(4)
        val updatedBuiltArtifacts = BuiltArtifactsLoaderImpl().load(
            project.layout.projectDirectory.dir(
                outputFolder.absolutePath
            )
        )

        Truth.assertThat(updatedBuiltArtifacts).isNotNull()
        Truth.assertThat(updatedBuiltArtifacts!!.elements).hasSize(3)
        updatedBuiltArtifacts.elements.forEach {
            Truth.assertThat(File(it.outputFile).absolutePath)
                .startsWith(outputFolder.absolutePath)
            Truth.assertThat(File(it.outputFile).readText(Charset.defaultCharset()))
                .isEqualTo(it.filters.joinToString { it.identifier })
        }
    }

    private fun createBuiltArtifact(
        outputFolder: File,
        fileName: String,
        versionCode: Int,
        densityValue: String
    ) =
        BuiltArtifactImpl(
            outputFile = File(outputFolder, "$fileName.xml").absolutePath,
            properties = mapOf("key1" to "value1", "key2" to "value2"),
            versionCode = versionCode,
            versionName = versionCode.toString(),
            isEnabled = true,
            outputType = VariantOutputConfiguration.OutputType.ONE_OF_MANY,
            filters = listOf(
                FilterConfiguration(FilterConfiguration.FilterType.DENSITY, densityValue)
            ),
            baseName = "someBaseName",
            fullName = "someFullName"
        )

    private fun createBuiltArtifacts(vararg elements: BuiltArtifactImpl) =
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = elements.toList()
        )
}