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
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.ide.model.sync.Variant
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

internal class AbstractVariantModelTaskTest {
    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var taskProvider: TaskProvider<TestClass>

    abstract class TestClass: AbstractVariantModelTask() {

        override fun addVariantContent(variant: Variant.Builder) {
        }
    }

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        taskProvider = project.tasks.register(
            "abstractVariantModelTaskTest",
            TestClass::class.java,
        )
    }

    @Test
    fun testHandleProvider() {
        val creationConfig = Mockito.mock(ApplicationCreationConfig::class.java)
        val artifacts = Mockito.mock(ArtifactsImpl::class.java)
        val providerRequestImpl = Mockito.mock(SingleInitialProviderRequestImpl::class.java)
        val creationAction = object: AbstractVariantModelTask.CreationAction<TestClass, ComponentCreationConfig>(creationConfig) {
            override val type: Class<TestClass>
                get() = TestClass::class.java
        }
        Mockito.`when`(creationConfig.artifacts).thenReturn(artifacts)
        @Suppress("UNCHECKED_CAST")
        Mockito.`when`(artifacts.setInitialProvider(taskProvider, AbstractVariantModelTask::outputModelFile))
            .thenReturn(providerRequestImpl as SingleInitialProviderRequestImpl<TestClass, RegularFile>?)
        creationAction.handleProvider(taskProvider)

        Mockito.verify(providerRequestImpl).on(InternalArtifactType.VARIANT_MODEL)
    }
}
