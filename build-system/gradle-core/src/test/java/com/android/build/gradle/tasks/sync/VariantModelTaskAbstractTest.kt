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

import com.android.ide.model.sync.Variant
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream

abstract class VariantModelTaskAbstractTest<T: AbstractVariantModelTask> {
    @get: Rule
    val temporaryFolder = TemporaryFolder()

    lateinit var project: Project
    lateinit var taskProvider: TaskProvider<T>
    lateinit var task: T

    fun setUp(taskType: Class<T>) {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        taskProvider = project.tasks.register(
            "variantModelTaskTest",
            taskType,
        )
        task = taskProvider.get()
    }

    fun testTaskAction(given: (T)->Unit, expect: (Variant)->Unit) {
        val modelFile = project.objects.fileProperty().also {
            it.set(temporaryFolder.newFile("variant_model.proto"))
        }
        task.outputModelFile.set(modelFile)
        given.invoke(task)

        task.taskAction()

        modelFile.asFile.get().let { outputFile ->
            Truth.assertThat(outputFile.exists()).isTrue()
            FileInputStream(outputFile).use {
                expect.invoke(Variant.parseFrom(it))
            }
        }
    }
}
