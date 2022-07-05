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

import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes
import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.LintCompile
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestResultAggregationTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.PackageForUnitTest
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import org.gradle.api.Task
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertEquals

class BuildAnalyzerTest {

    @Test
    fun `all tasks have build analyzer annotations`() {
        val allTasks = getAllTasks()
        // When annotations for all tasks are added,
        // can just assert missingTasks is empty
        val missingTasks = allTasks.filter {
            !it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        val tasksWithAnnotations = allTasks.filter {
            it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        assertThat(missingTasks).containsNoneIn(TASKS_WITH_ANNOTATIONS)
        assertThat(tasksWithAnnotations).containsExactlyElementsIn(TASKS_WITH_ANNOTATIONS)
        assertEquals(allTasks.size,missingTasks.size + TASKS_WITH_ANNOTATIONS.size)
    }

    //  List of tasks which has BuildAnalyzerAnnotation, added manually
    private val TASKS_WITH_ANNOTATIONS = listOf(
            AidlCompile::class.java,
            JavaPreCompileTask::class.java,
            CompileArtProfileTask::class.java,
            LintCompile::class.java,
            CompileLibraryResourcesTask::class.java,
            RenderscriptCompile::class.java,
            ShaderCompile::class.java,
            AndroidReportTask::class.java,
            AndroidUnitTest::class.java,
            DeviceProviderInstrumentTestTask::class.java,
            JacocoReportTask::class.java,
            TestServerTask::class.java,
            GenerateTestConfig::class.java,
            PackageForUnitTest::class.java,
            JacocoTask::class.java,
            ManagedDeviceInstrumentationTestTask::class.java,
            DeviceSerialTestTask::class.java,
            ManagedDeviceInstrumentationTestResultAggregationTask::class.java,
    ) as List<Class<*>>

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
