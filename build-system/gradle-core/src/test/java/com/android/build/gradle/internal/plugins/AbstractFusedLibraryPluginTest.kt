/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.Artifact
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.testutils.MockitoKt.any
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AbstractFusedLibraryPluginTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var softwareComponentFactory: SoftwareComponentFactory

    @Mock
    lateinit var listenerRegistry: BuildEventsListenerRegistry

    @Mock
    lateinit var taskFactory: TaskFactoryImpl

    val plugin by lazy {
        object : AbstractFusedLibraryPlugin<PrivacySandboxSdkVariantScope>(
            softwareComponentFactory,
            listenerRegistry
        ) {
            override val variantScope: PrivacySandboxSdkVariantScope
                get() = Mockito.mock(PrivacySandboxSdkVariantScope::class.java)

            override fun createTasks(project: Project) {
                throw java.lang.IllegalStateException("No call expected on this sub method")
            }

            override val artifactTypeForPublication: Artifact.Single<RegularFile>?
                get() = throw java.lang.IllegalStateException("No call expected on this sub method")

            override fun configureProject(project: Project) {
                throw java.lang.IllegalStateException("No call expected on this sub method")
            }

            override fun configureExtension(project: Project) {
                throw java.lang.IllegalStateException("No call expected on this sub method")
            }

            override fun getAnalyticsPluginType(): GradleBuildProject.PluginType? {
                throw java.lang.IllegalStateException("No call expected on this sub method")
            }

            init {
                project = Mockito.mock(Project::class.java)
            }
        }
    }

    @Test
    fun testCreateCreationAction() {

        class TestTask: DefaultTask() {}

        class TestCreationAction(
            val creationConfig: PrivacySandboxSdkVariantScope
        ): TaskCreationAction<TestTask>() {

            override val name: String
                get() = "testCreation"

            override val type: Class<TestTask>
                get() = TestTask::class.java

            override fun configure(task: TestTask) {}
        }

        Mockito.`when`(taskFactory.register(any(TestCreationAction::class.java)))
            .thenReturn(Mockito.mock(TaskProvider::class.java) as TaskProvider<TestTask>)

        val creationAction = plugin.createCreationAction(
            taskFactory,
            TestCreationAction::class.java,
            PrivacySandboxSdkVariantScope::class.java
        )
        Truth.assertThat(creationAction).isNotNull()
        Truth.assertThat(creationAction is TaskProvider).isTrue()
    }

    @Test
    fun testCreateCreationActionWithSuperType() {

        class TestTask: DefaultTask() {}

        class TestCreationAction(
            val creationConfig: FusedLibraryVariantScope
        ): TaskCreationAction<TestTask>() {

            override val name: String
                get() = "testCreation"

            override val type: Class<TestTask>
                get() = TestTask::class.java

            override fun configure(task: TestTask) {}
        }

        Mockito.`when`(taskFactory.register(any(TestCreationAction::class.java)))
            .thenReturn(Mockito.mock(TaskProvider::class.java) as TaskProvider<TestTask>)


        // call the createCreationAction with a constructor parameter that is a sub type of the
        // declared constructor parameter (see above).
        val creationAction = plugin.createCreationAction(
            taskFactory,
            TestCreationAction::class.java,
            PrivacySandboxSdkVariantScope::class.java
        )
        Truth.assertThat(creationAction).isNotNull()
        Truth.assertThat(creationAction is TaskProvider).isTrue()
    }

    @Test(expected = NoSuchMethodException::class)
    fun testCreateCreationActionWithIncorrectConstructorParameterType() {

        class TestTask: DefaultTask() {}

        class TestCreationAction(
            val creationConfig: ApplicationCreationConfig
        ): TaskCreationAction<TestTask>() {

            override val name: String
                get() = "testCreation"

            override val type: Class<TestTask>
                get() = TestTask::class.java

            override fun configure(task: TestTask) {}
        }

        // call the createCreationAction with a constructor parameter that is not a sub type of the
        // declared constructor parameter (see above).
        val creationAction = plugin.createCreationAction(
            taskFactory,
            TestCreationAction::class.java,
            PrivacySandboxSdkVariantScope::class.java
        )
        Truth.assertThat(creationAction).isNotNull()
        Truth.assertThat(creationAction is TaskProvider).isTrue()
    }
}
