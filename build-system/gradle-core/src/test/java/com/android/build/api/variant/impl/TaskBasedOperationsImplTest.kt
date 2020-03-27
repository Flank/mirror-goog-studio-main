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
import com.android.build.api.artifact.impl.ArtifactTransformationRequestImpl
import com.android.build.api.artifact.impl.OperationsImpl
import com.android.build.api.artifact.impl.ProfilerEnabledWorkQueue
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.gradle.internal.profile.ProfileAgent
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.builder.profile.ProfileRecordWriter
import com.android.ide.common.workers.GradlePluginMBeans
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.TaskState
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
import java.io.Serializable
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Tests for TaskBasedOperationsImpl
 */
class TaskBasedOperationsImplTest {

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
            createBuiltArtifact(inputFolder, "file1", "xhdpi"),
            createBuiltArtifact(inputFolder, "file2", "xxhdpi"),
            createBuiltArtifact(inputFolder, "file3", "xxxhdpi")
        ).save(inputFolderProperty.get())

        operations.getArtifactContainer(InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST)
            .setInitialProvider(inputFolderProperty)
        Mockito.`when`(component.operations).thenReturn(operations)
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
            replacementRequest.submit(this) { builtArtifact ->
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
                    .andWrite(type = InternalArtifactType.PACKAGED_MANIFESTS, at = SynchronousTask::outputDir)
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
        val mergedManifests = operations.get(InternalArtifactType.PACKAGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        val task = taskProvider.get()
        task.execute()
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<*, SynchronousTask>).wrapUp(task)

        checkOutputFolderCorrectness(outputFolder)
    }

    abstract class InternalApiTask @Inject constructor(
        val objectFactory: ObjectFactory,
        var workers: WorkerExecutor): VariantAwareTask, DefaultTask() {

        @get:InputFiles
        abstract val inputDir: DirectoryProperty
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        private val waiting = AtomicBoolean(false)
        val useProfiler = AtomicBoolean(false)

        @get:Internal
        lateinit var replacementRequest: ArtifactTransformationRequest

        interface WorkItemParameters: WorkParameters, Serializable {
            val builtArtifact: Property<BuiltArtifact>
            val outputFile: Property<File>
            val someInputToWorkerItem: Property<Int>
        }

        abstract class WorkItem @Inject constructor(private val workItemParameters: WorkItemParameters): WorkAction<WorkItemParameters> {
            override fun execute() {
                Truth.assertThat(workItemParameters.someInputToWorkerItem.get()).isAnyOf(1,2,3)
                workItemParameters.outputFile.get().writeText(
                    workItemParameters.builtArtifact.get().filters.joinToString { it.identifier })
            }
        }

        @TaskAction
        fun execute() {
            val counter = AtomicInteger(0)
            // depending if profiler information is requested, invoke the right submit API.
            val updatedBuiltArtifacts = if (useProfiler.get()) {
                (replacementRequest as ArtifactTransformationRequestImpl<*, InternalApiTask>).submitWithProfiler(
                    this,
                    objectFactory,
                    workers.noIsolation(),
                    WorkItem::class.java,
                    WorkItemParameters::class.java
                ) { builtArtifact: BuiltArtifact, outputLocation: Directory, workItemParameters: WorkItemParameters ->
                    workItemParameters.someInputToWorkerItem.set(counter.incrementAndGet())
                    workItemParameters.builtArtifact.set(builtArtifact)
                    workItemParameters.outputFile.set(outputLocation.file(File(builtArtifact.outputFile).name).asFile)
                    workItemParameters.outputFile.get()
                }
            } else {
                replacementRequest.submit(
                    this,
                    workers.noIsolation(),
                    WorkItem::class.java,
                    WorkItemParameters::class.java
                ) {builtArtifact: BuiltArtifact, outputLocation: Directory, workItemParameters: WorkItemParameters ->
                    workItemParameters.someInputToWorkerItem.set(counter.incrementAndGet())
                    workItemParameters.builtArtifact.set(builtArtifact)
                    workItemParameters.outputFile.set(outputLocation.file(File(builtArtifact.outputFile).name).asFile)
                    workItemParameters.outputFile.get()
                }
            }
            if (waiting.get()) {
                updatedBuiltArtifacts.get()
            }
        }

        class CreationAction(val component: VariantPropertiesImpl): VariantTaskCreationAction<InternalApiTask, VariantPropertiesImpl>(component) {
            override val name: String
                get() = component.computeTaskName("foo", "bar")
            override val type: Class<InternalApiTask>
                get() = InternalApiTask::class.java

            private lateinit var replacementRequest: ArtifactTransformationRequest

            override fun handleProvider(taskProvider: TaskProvider<out InternalApiTask>) {
                super.handleProvider(taskProvider)
                replacementRequest = component.operations
                    .use(taskProvider)
                    .toRead(type = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST, at = InternalApiTask::inputDir)
                    .andWrite(type = InternalArtifactType.PACKAGED_MANIFESTS, at = InternalApiTask::outputDir)
            }

            override fun configure(task: InternalApiTask) {
                task.variantName = "debug"
                task.replacementRequest = replacementRequest
            }
        }
    }

    @Test
    fun asynchronousApiTest() {
        asynchronousTest(
            workers =  getFakeWorkerExecutor(false, ":replace", InternalApiTask.WorkItemParameters::class.java),
            withProfiler = false)
    }

    @Test
    fun asynchronousWaitingTest() {
        asynchronousTest(
            workers = getFakeWorkerExecutor(true, ":replace", InternalApiTask.WorkItemParameters::class.java),
            withProfiler = false)
    }

    private fun asynchronousTest(
        workers: WorkerExecutor,
        withProfiler: Boolean,
        recordingBuildListener: RecordingBuildListener? = null) {

        val taskACreationAction = InternalApiTask.CreationAction(component)
        val taskProvider= project.tasks.register("replace", InternalApiTask::class.java)
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
        val mergedManifests = operations.get(InternalArtifactType.PACKAGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        val task = taskProvider.get()
        task.workers = workers
        task.useProfiler.set(withProfiler)
        recordingBuildListener?.beforeExecute(task)
        task.execute()
        recordingBuildListener?.afterExecute(task, Mockito.mock(TaskState::class.java))

        // force wrap up
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<*, InternalApiTask>).wrapUp(task)

        checkOutputFolderCorrectness(outputFolder)
    }

    @Test
    fun internalAgpApiTest_Waiting() {
        internalAgpApiTestWithProfiler(true)
    }

    @Test
    fun internalAgpApiTest_Not_Waiting() {
        internalAgpApiTestWithProfiler(false)
    }

    private fun internalAgpApiTestWithProfiler(waiting: Boolean) {

        val recordWriter= object: ProfileRecordWriter {
            val counter = AtomicLong(0)
            val records = mutableListOf<GradleBuildProfileSpan>()
            override fun allocateRecordId(): Long = counter.incrementAndGet()
            override fun writeRecord(
                project: String,
                variant: String?,
                executionRecord: GradleBuildProfileSpan.Builder,
                taskExecutionPhases: MutableList<GradleBuildProfileSpan>
            ) {
                records.add(executionRecord.build())
            }
        }

        val projectName = "project_name_${tmpDir.root.path.hashCode()}"
        val recordingBuildListener =
            RecordingBuildListener(projectName, recordWriter)
        try {
            ProfileAgent.register(projectName, recordingBuildListener)
            asynchronousTest(
                workers = getFakeWorkerExecutor(waiting = waiting,
                    taskPath = ":replace",
                    parameterType = WorkActionAdapter.AdaptedWorkParameters::class.java,
                    includeProfiler = true),
                withProfiler = true,
                recordingBuildListener = recordingBuildListener)
            val profileMBean =
                GradlePluginMBeans.getProfileMBean(projectName)
            Truth.assertThat(profileMBean).isNotNull()
            // assert that the task span was written.
            Truth.assertThat(recordWriter.records).hasSize(1)
        } finally {
            ProfileAgent.unregister()
        }
    }

    /**
     * Create fake [WorkerExecutor] that can only return a WorkQueue processing its job
     * synchronously.
     *
     * @param waiting true if [WorkQueue.await] is supposed to be called.
     * @param taskPath Task path used for profiling information.
     * @param parameterType Class for the parameters used when executing [WorkQueue] submissions.
     * @param includeProfiler true if the profiler information should be included.
     */
    private fun getFakeWorkerExecutor(
        waiting: Boolean,
        taskPath: String,
        parameterType: Class<*>,
        includeProfiler: Boolean = false): WorkerExecutor {

        var workQueue: WorkQueue = object: WorkQueue {

            override fun <T : WorkParameters?> submit(
                workActionType: Class<out WorkAction<T>>,
                workParametersCustomizer: Action<in T>
            ) {
                val parameters = project.objects.newInstance(parameterType)
                @Suppress("UNCHECKED_CAST")
                workParametersCustomizer.execute(parameters as T)
                val workItemAction = project.objects.newInstance(workActionType, parameters)
                workItemAction.execute()
            }

            override fun await() {
                Assert.assertTrue("Task was not supposed to wait for results", waiting)
            }
        }
        if (includeProfiler) {
            workQueue = ProfilerEnabledWorkQueue("project_name_" + tmpDir.root.path.hashCode(), taskPath, workQueue)
        }

        // replace injected WorkerExecutor with dummy version.
        val workerExecutor = Mockito.mock(WorkerExecutor::class.java)
        Mockito.`when`(workerExecutor.noIsolation()).thenReturn(workQueue)
        return workerExecutor
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
        densityValue: String
    ) =
        BuiltArtifactImpl.make(
            outputFile = File(outputFolder, "$fileName.xml").absolutePath,
            versionCode = 12,
            versionName = "12",
            variantOutputConfiguration = VariantOutputConfigurationImpl(
                isUniversal = false,
                filters = listOf(
                    FilterConfiguration(FilterConfiguration.FilterType.DENSITY, densityValue)
                ))
        )

    private fun createBuiltArtifacts(vararg elements: BuiltArtifactImpl) =
        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
            applicationId = "com.android.test",
            variantName = "debug",
            elements = elements.toList()
        )
}