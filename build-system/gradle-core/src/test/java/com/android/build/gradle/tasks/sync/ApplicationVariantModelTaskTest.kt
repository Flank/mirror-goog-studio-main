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
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.FileInputStream

/**
 * Tests for [ApplicationVariantModelTask]
 */
internal class ApplicationVariantModelTaskTest: VariantModelTaskAbstractTest<ApplicationVariantModelTask>() {

    @Before
    fun setUp() {
        super.setUp(ApplicationVariantModelTask::class.java)
    }

    @Test
    fun testTaskAction() {
        super.testTaskAction(
                given = {
                    it.applicationId.set("testApplicationId")
                    it.setupModuleTaskInputs()
                },
                expect = {
                    assertThat(it.variantCase).isEqualTo(Variant.VariantCase.APPLICATIONVARIANTMODEL)
                    it.applicationVariantModel.moduleCommonModel.testModuleFields()
                    assertThat(it.applicationVariantModel).isNotNull()
                    assertThat(it.applicationVariantModel.applicationId)
                            .isEqualTo("testApplicationId")                }
        )
    }

    @Test
    fun testConfigure() {
        val creationConfig = Mockito.mock(ApplicationCreationConfig::class.java)
        Mockito.`when`(creationConfig.applicationId).thenReturn(project.provider { "testAppId" })
        Mockito.`when`(creationConfig.manifestPlaceholders).thenReturn(
                project.objects.mapProperty(String::class.java, String::class.java).also {
                    it.put("key1", "value1")
                    it.put("key2", "value2")
                }
        )

        Mockito.`when`(creationConfig.name).thenReturn("debug")
        Mockito.`when`(creationConfig.services).thenReturn(
            createTaskCreationServices(createProjectServices(project))
        )
        val creationAction = ApplicationVariantModelTask.CreationAction(creationConfig)
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            FakeNoOpAnalyticsService::class.java
        ) {}
        creationAction.configure(task)

        task.assertModuleTaskInputs()
        assertThat(task.applicationId.get()).isEqualTo("testAppId")
    }
}
