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

import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.ide.model.sync.Variant
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.FileInputStream

/**
 * [org.gradle.api.Task] to create the sync model file for
 * [com.android.build.api.variant.ApplicationVariant].
 *
 * The task is not incremental and not cacheable as execution should be so fast, that it outweighs
 * the benefits in performance.
 */
internal class ApplicationVariantModelTaskTest: VariantModelTaskAbstractTest<ApplicationVariantModelTask>() {

    @Before
    fun setUp() {
        super.setUp(ApplicationVariantModelTask::class.java)
    }

    @Test
    fun testTaskAction() {
        val modelFile = project.objects.fileProperty().also {
            it.set(temporaryFolder.newFile("variant_model.pb"))
        }
        task.outputModelFile.set(modelFile)
        task.applicationId.set("testApplicationId")

        task.taskAction()

        modelFile.asFile.get().let { outputFile ->
            assertThat(outputFile.exists()).isTrue()
            FileInputStream(outputFile).use {
                val variant = Variant.parseFrom(it)
                assertThat(variant.variantCase).isEqualTo(Variant.VariantCase.APPLICATIONVARIANTMODEL)
                assertThat(variant.applicationVariantModel).isNotNull()
                assertThat(variant.applicationVariantModel.applicationId)
                        .isEqualTo("testApplicationId")
            }
        }
    }

    @Test
    fun testConfigure() {
        val creationConfig = Mockito.mock(ApplicationCreationConfig::class.java)
        Mockito.`when`(creationConfig.applicationId).thenReturn(project.provider { "testAppId" })
        Mockito.`when`(creationConfig.name).thenReturn("debug")
        Mockito.`when`(creationConfig.services).thenReturn(
            createTaskCreationServices(
                createProjectServices(
                    projectInfo = ProjectInfo(project)
                )
            )
        )
        val creationAction = ApplicationVariantModelTask.CreationAction(creationConfig)
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            FakeNoOpAnalyticsService::class.java
        ) {}
        creationAction.configure(task)

        assertThat(task.applicationId.get()).isEqualTo("testAppId")
    }
}
