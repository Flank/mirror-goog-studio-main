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
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.ProfileAgent
import com.android.build.gradle.internal.profile.RecordingBuildListener
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
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
    private lateinit var artifacts: ArtifactsImpl
    private val taskInitialized = AtomicBoolean(false)
    private val component = Mockito.mock(VariantPropertiesImpl::class.java)

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(
            tmpDir.newFolder()).build()
        artifacts = ArtifactsImpl(project, "debug")

        val inputFolder = tmpDir.newFolder("input")
        val inputFolderProperty = project.objects.directoryProperty().also { it.set(inputFolder) }
        createBuiltArtifacts(
            createBuiltArtifact(inputFolder, "file1", "xhdpi"),
            createBuiltArtifact(inputFolder, "file2", "xxhdpi"),
            createBuiltArtifact(inputFolder, "file3", "xxxhdpi")
        ).save(inputFolderProperty.get())

        artifacts.getArtifactContainer(InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST)
            .setInitialProvider(inputFolderProperty)
        Mockito.`when`(component.artifacts).thenReturn(artifacts)
    }

    abstract class SynchronousTask @Inject constructor(val workers: WorkerExecutor): VariantAwareTask, DefaultTask() {

        @get:InputFiles
        abstract val inputDir: DirectoryProperty
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @get:Internal
        lateinit var replacementRequest: ArtifactTransformationRequest<SynchronousTask>

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

            private lateinit var replacementRequest: ArtifactTransformationRequest<SynchronousTask>

            override fun handleProvider(taskProvider: TaskProvider<SynchronousTask>) {
                super.handleProvider(taskProvider)
                replacementRequest = component.artifacts
                    .use(taskProvider)
                    .wiredWithDirectories(
                        SynchronousTask::inputDir,
                        SynchronousTask::outputDir)
                    .toTransformMany(
                        InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
                        InternalArtifactType.PACKAGED_MANIFESTS)
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
        val mergedManifests = artifacts.get(InternalArtifactType.PACKAGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        val task = taskProvider.get()
        task.execute()
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<SynchronousTask>).wrapUp(task)

        checkOutputFolderCorrectness(outputFolder)
    }

    abstract class InternalApiTask @Inject constructor(
        var workers: WorkerExecutor
    ): VariantAwareTask, NonIncrementalGlobalTask() {

        @get:InputFiles
        abstract val inputDir: DirectoryProperty
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        private val waiting = AtomicBoolean(false)

        @get:Internal
        lateinit var replacementRequest: ArtifactTransformationRequest<InternalApiTask>

        interface WorkItemParameters: DecoratedWorkParameters {
            val builtArtifact: Property<BuiltArtifact>
            val outputFile: Property<File>
            val someInputToWorkerItem: Property<Int>
        }

        abstract class WorkItem @Inject constructor(
            private val workItemParameters: WorkItemParameters
        ): WorkActionAdapter<WorkItemParameters> {
            override fun getParameters(): WorkItemParameters {
                return workItemParameters
            }



            override fun doExecute() {
                Truth.assertThat(workItemParameters.someInputToWorkerItem.get()).isAnyOf(1,2,3)
                workItemParameters.outputFile.get().writeText(
                    workItemParameters.builtArtifact.get().filters.joinToString { it.identifier })
            }
        }

        override fun doTaskAction() {
            val counter = AtomicInteger(0)
            // depending if profiler information is requested, invoke the right submit API.
            val updatedBuiltArtifacts =
                (replacementRequest as ArtifactTransformationRequestImpl<InternalApiTask>).submit(
                    this,
                    workers.noIsolation(),
                    WorkItem::class.java
                ) { builtArtifact: BuiltArtifact, outputLocation: Directory, workItemParameters: WorkItemParameters ->
                    workItemParameters.someInputToWorkerItem.set(counter.incrementAndGet())
                    workItemParameters.builtArtifact.set(builtArtifact)
                    workItemParameters.outputFile.set(outputLocation.file(File(builtArtifact.outputFile).name).asFile)
                    workItemParameters.outputFile.get()
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

            private lateinit var replacementRequest: ArtifactTransformationRequest<InternalApiTask>

            override fun handleProvider(taskProvider: TaskProvider<InternalApiTask>) {
                super.handleProvider(taskProvider)
                replacementRequest = component.artifacts
                    .use(taskProvider)
                    .wiredWithDirectories(
                        InternalApiTask::inputDir,
                        InternalApiTask::outputDir)
                    .toTransformMany(
                        InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST,
                        InternalArtifactType.PACKAGED_MANIFESTS)
            }

            override fun configure(task: InternalApiTask) {
                task.replacementRequest = replacementRequest
            }
        }
    }

    @Test
    fun asynchronousApiTest() {
        asynchronousTest(workers =  getFakeWorkerExecutor(false))
    }

    @Test
    fun asynchronousWaitingTest() {
        asynchronousTest(workers = getFakeWorkerExecutor(true))
    }

    private fun asynchronousTest(
        workers: WorkerExecutor,
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
        val mergedManifests = artifacts.get(InternalArtifactType.PACKAGED_MANIFESTS)
        mergedManifests.get()
        Truth.assertThat(taskInitialized.get()).isTrue()

        val task = taskProvider.get()
        task.workers = workers
        recordingBuildListener?.beforeExecute(task)
        task.taskAction()
        recordingBuildListener?.afterExecute(task, Mockito.mock(TaskState::class.java))

        // force wrap up
        @Suppress("UNCHECKED_CAST")
        (task.replacementRequest as ArtifactTransformationRequestImpl<InternalApiTask>).wrapUp(task)

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
                workers = getFakeWorkerExecutor(waiting = waiting),
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
     */
    private fun getFakeWorkerExecutor(waiting: Boolean): WorkerExecutor {
        return object: FakeGradleWorkExecutor(project.objects, tmpDir.newFolder()) {
            override fun await() {
                Assert.assertTrue("Task was not supposed to wait for results", waiting)
            }
        }
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
        updatedBuiltArtifacts.elements.forEach { builtArtifactImpl ->
            Truth.assertThat(File(builtArtifactImpl.outputFile).absolutePath)
                .startsWith(outputFolder.absolutePath)
            Truth.assertThat(File(builtArtifactImpl.outputFile).readText(Charset.defaultCharset()))
                .isEqualTo(builtArtifactImpl.filters.joinToString { it.identifier })
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