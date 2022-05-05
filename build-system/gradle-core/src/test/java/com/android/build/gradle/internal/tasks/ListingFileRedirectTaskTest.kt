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
import java.nio.file.Path
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider

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
        // test main case with relative path between redirectFile and listingFile
        Truth.assertThat(redirectFile.readLines()[1]).isEqualTo("listingFile=../folder1/listingFile")
    }

    @Test
    fun testDifferentRootTestCase() {
        val spyTask = Mockito.spy(task)

        val folder1 = temporaryFolder.newFolder("folder1")
        val folder2 = temporaryFolder.newFolder("folder2")
        val redirectFile = File(folder2, "redirectFile")
        val spyRedirectFile = Mockito.spy(redirectFile)
        val listingFile = File(folder1.path,"listingFile")

        //need to mock file.parent.toPath.relativize for ListingFileRedirect.writeRedirect
        val mockPath = Mockito.mock(Path::class.java)
        val mockParent = Mockito.mock(File::class.java)
        Mockito.`when`(spyRedirectFile.parentFile).thenReturn(mockParent)
        Mockito.`when`(mockParent.toPath()).thenReturn(mockPath)
        Mockito.`when`(mockPath.relativize(listingFile.toPath()))
            .thenThrow(IllegalArgumentException("Different roots"))

        //need to mock task.redirectFile.asFile.get()
        val mockFileProperty = Mockito.mock(RegularFileProperty::class.java)
        @Suppress("UNCHECKED_CAST")
        val mockProvider = Mockito.mock(Provider::class.java) as Provider<File>
        Mockito.`when`(spyTask.redirectFile).thenReturn(mockFileProperty)
        Mockito.`when`(mockFileProperty.getAsFile()).thenReturn(mockProvider)
        Mockito.`when`(mockProvider.get()).thenReturn(spyRedirectFile)

        spyTask.listingFile.set(project.objects.fileProperty().also {
            it.set(listingFile)
        })

        spyTask.taskAction()
        // test edge case when redirectFile and listingFile has different roots
        Truth.assertThat(redirectFile.readLines()[1])
            .isEqualTo("listingFile=" + listingFile.canonicalPath.replace("\\","/"))
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
