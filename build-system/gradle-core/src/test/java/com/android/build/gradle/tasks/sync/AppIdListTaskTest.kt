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

package com.android.build.gradle.tasks.sync

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.SingleInitialProviderRequestImpl
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.ide.model.sync.AppIdListSync
import com.android.ide.model.sync.AppIdSync
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
import java.io.BufferedInputStream
import java.io.FileInputStream

internal class AppIdListTaskTest {
    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var taskProvider: TaskProvider<AppIdListTask>

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        taskProvider = project.tasks.register(
            "appIdListTaskTest",
            AppIdListTask::class.java,
        )
    }

    @Test
    fun testHandleProvider() {
        val creationConfig = Mockito.mock(GlobalTaskCreationConfig::class.java)
        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        val providerRequestImpl = Mockito.mock(SingleInitialProviderRequestImpl::class.java)
        val creationAction = AppIdListTask.CreationAction(creationConfig, mapOf())
        Mockito.`when`(creationConfig.globalArtifacts).thenReturn(artifacts)
        @Suppress("UNCHECKED_CAST")
        Mockito.`when`(artifacts.setInitialProvider(taskProvider, AppIdListTask::outputModelFile))
            .thenReturn(providerRequestImpl as SingleInitialProviderRequestImpl<AppIdListTask, RegularFile>?)
        Mockito.`when`(providerRequestImpl.withName("app_id_list.pb")).thenReturn(
            providerRequestImpl
        )
        creationAction.handleProvider(taskProvider)

        Mockito.verify(providerRequestImpl).on(InternalArtifactType.APP_ID_LIST_MODEL)
    }

    @Test
    fun testResultingProto() {
        val protoFile = temporaryFolder.newFile("resulting.proto")
        taskProvider.get().also { appIdListTask ->
            appIdListTask.outputModelFile.set(protoFile)
            appIdListTask.variantsApplicationId.addAll(
                project.objects.newInstance(AppIdListTask.VariantInformation::class.java).also {
                    it.variantName.set("fooBarDebug")
                    it.applicationId.set("com.example.foo.bar")
                },
                project.objects.newInstance(AppIdListTask.VariantInformation::class.java).also {
                    it.variantName.set("fooBarStaging")
                    it.applicationId.set("com.example.foo.bar.staging")
                },
            )
            appIdListTask.doTaskAction()
        }

        // check resulting proto file.
        Truth.assertThat(protoFile.exists()).isTrue()
        val listOfAppIds = AppIdListSync.parseFrom(BufferedInputStream(FileInputStream(protoFile)))
        Truth.assertThat(listOfAppIds).isNotNull()
        val variants = listOfAppIds.appIdsList
        Truth.assertThat(variants).hasSize(2)
        val mapOfAppIds: Map<String, AppIdSync> = variants.associateBy { appId -> appId.name }
        Truth.assertThat(mapOfAppIds["fooBarDebug"]).isNotNull()
        Truth.assertThat(mapOfAppIds["fooBarDebug"]!!.applicationId)
            .isEqualTo("com.example.foo.bar")
        Truth.assertThat(mapOfAppIds["fooBarStaging"]).isNotNull()
        Truth.assertThat(mapOfAppIds["fooBarStaging"]!!.applicationId)
            .isEqualTo("com.example.foo.bar.staging")
    }
}
