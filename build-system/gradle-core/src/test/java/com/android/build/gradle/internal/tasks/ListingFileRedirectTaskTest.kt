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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.SingleInitialProviderRequestImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.ide.common.build.ListingFileRedirect
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.File

internal class ListingFileRedirectTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var taskCreationServices: TaskCreationServices
    private lateinit var taskProvider: TaskProvider<ListingFileRedirectTask>
    private lateinit var task: ListingFileRedirectTask

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()

        taskCreationServices = createTaskCreationServices(createProjectServices(project = project))

        @Suppress("UnstableApiUsage")
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            FakeNoOpAnalyticsService::class.java
        ) {}
        taskProvider = project.tasks.register(
            "listingFileTest",
            ListingFileRedirectTask::class.java,
        )
        task = taskProvider.get()
    }

    @Test
    fun testTaskExecution() {
        val folder1 = temporaryFolder.newFolder("folder1")
        val folder2 = temporaryFolder.newFolder("folder2")
        task.listingFile.set(project.objects.fileProperty().also {
            it.set(
                File(folder1, "listingFile")
            )
        })
        val redirectFile = File(folder2, "redirectFile")
        task.redirectFile.set(project.objects.fileProperty().also {
            it.set(redirectFile)
        })
        task.taskAction()
        Truth.assertThat(redirectFile.readLines()[1]).isEqualTo("listingFile=../folder1/listingFile")
    }

    @Test
    fun testTaskConfiguration() {
        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        val creationConfig = Mockito.mock(ComponentCreationConfig::class.java)
        Mockito.`when`(creationConfig.artifacts).thenReturn(artifacts)
        Mockito.`when`(creationConfig.services).thenReturn(taskCreationServices)
        val artifactType = object : Artifact.Single<RegularFile>(
            ArtifactKind.FILE,
            Category.INTERMEDIATES) {}
        val creationAction = ListingFileRedirectTask.CreationAction(
            creationConfig = creationConfig,
            taskSuffix = "debug",
            inputArtifactType = InternalArtifactType.APK_IDE_MODEL,
            outputArtifactType = artifactType
        )

        @Suppress("UNCHECKED_CAST")
        val request = Mockito.mock(SingleInitialProviderRequestImpl::class.java)
                as SingleInitialProviderRequestImpl<ListingFileRedirectTask, RegularFile>
        Mockito.`when`(artifacts.setInitialProvider(
            taskProvider,
            ListingFileRedirectTask::redirectFile)
        ).thenReturn(request)
        Mockito.`when`(request.withName(ListingFileRedirect.REDIRECT_FILE_NAME)).thenReturn(request)
        creationAction.handleProvider(taskProvider)
        Mockito.verify(request).on(artifactType)

        val listingFile = temporaryFolder.newFile("listingFile_file")
        Mockito.`when`(artifacts.get(InternalArtifactType.APK_IDE_MODEL)).thenReturn(
            project.objects.fileProperty().also {
                it.set(listingFile)
            }
        )
        creationAction.configure(task)
        Mockito.verify(artifacts).get(InternalArtifactType.APK_IDE_MODEL)

        Truth.assertThat(task.listingFile.isPresent).isTrue()
        Truth.assertThat(task.listingFile.get().asFile).isEqualTo(listingFile)
    }
}
