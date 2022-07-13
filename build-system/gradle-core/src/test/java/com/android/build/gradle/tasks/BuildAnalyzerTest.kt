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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.AnalyticsRecordingTask
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.AppPreBuildTask
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.GeneratePrivacySandboxSdkRuntimeConfigFile
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TestPreBuildTask
import com.android.build.gradle.tasks.sync.AppIdListTask
import org.gradle.api.Task
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertEquals

class BuildAnalyzerTest {

    @Test
    fun `all tasks have build analyzer annotations unless in allow-list`() {
        val allTasks = getAllTasks()
        val actualTasksWithoutAnnotations = allTasks.filter {
            !it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        // Make sure a task was not added twice in allow-list
        assertEquals(expectedTasksWithoutAnnotations.toSet().size, expectedTasksWithoutAnnotations.size)
        // Tasks without annotations has to be added into allow-list, otherwise it needs annotation
        assertThat(actualTasksWithoutAnnotations).containsExactlyElementsIn(expectedTasksWithoutAnnotations)
    }

    // Allow-list of tasks that are defined to not have annotations.
    // Usually due to it being a base task, and hence is implemented by other tasks.
    // Another reason is that it does not fall into any TaskCategoryLabel,
    // and the task will not show up in Build Analyzer anyway.
    private val expectedTasksWithoutAnnotations = listOf(
            AppPreBuildTask::class.java,
            TestPreBuildTask::class.java,
            AndroidVariantTask::class.java,
            NewIncrementalTask::class.java,
            NonIncrementalTask::class.java,
            PackageAndroidArtifact::class.java,
            BaseTask::class.java,
            NonIncrementalGlobalTask::class.java,
            AnalyticsRecordingTask::class.java,
            BuildPrivacySandboxSdkApks::class.java,
            GeneratePrivacySandboxAsar::class.java,
            GeneratePrivacySandboxSdkRuntimeConfigFile::class.java,
            AppIdListTask::class.java
    )

    private fun getAllTasks(): List<Class<*>> {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val taskInterface = TypeToken.of(Task::class.java)
        return classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map{
                    classInfo -> classInfo.load() as Class<*>
                }
                .filter{
                    TypeToken.of(it).getTypes().contains(taskInterface)
                }
    }
}
