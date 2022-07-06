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
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask
import com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask
import com.android.build.gradle.internal.res.namespaced.CreateNonNamespacedLibraryManifestTask
import com.android.build.gradle.internal.res.namespaced.GenerateNamespacedLibraryRFilesTask
import com.android.build.gradle.internal.res.namespaced.LinkLibraryAndroidResourcesTask
import com.android.build.gradle.internal.res.namespaced.ProcessAndroidAppResourcesTask
import com.android.build.gradle.internal.res.namespaced.StaticLibraryManifestTask
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.AssetPackManifestGenerationTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.CheckManifest
import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.LinkManifestForAssetPackTask
import com.android.build.gradle.internal.tasks.LintCompile
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestResultAggregationTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import com.android.build.gradle.internal.tasks.PackageForUnitTest
import com.android.build.gradle.internal.tasks.ProcessAssetPackManifestTask
import com.android.build.gradle.internal.tasks.ShrinkResourcesOldShrinkerTask
import com.android.build.gradle.internal.tasks.TestServerTask
import com.android.build.gradle.internal.transforms.LegacyShrinkBundleModuleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkAppBundleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkResourcesNewShrinkerTask
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
        // Make sure a task was not added twice in list
        assertEquals(TASKS_WITH_ANNOTATIONS.toSet().size, TASKS_WITH_ANNOTATIONS.size)
        // Make sure tasks without annotation weren't accidentally added in
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
            CheckManifest::class.java,
            CompatibleScreensManifest::class.java,
            InvokeManifestMerger::class.java,
            ProcessApplicationManifest::class.java,
            ProcessLibraryManifest::class.java,
            ProcessTestManifest::class.java,
            StaticLibraryManifestTask::class.java,
            CreateNonNamespacedLibraryManifestTask::class.java,
            ManifestProcessorTask::class.java,
            AssetPackManifestGenerationTask::class.java,
            ProcessAssetPackManifestTask::class.java,
            LinkManifestForAssetPackTask::class.java,
            ProcessManifestForBundleTask::class.java,
            ProcessManifestForMetadataFeatureTask::class.java,
            ProcessManifestForInstantAppTask::class.java,
            ProcessPackagedManifestTask::class.java,
            ProcessMultiApkApplicationManifest::class.java,
            GenerateManifestJarTask::class.java,
            LinkLibraryAndroidResourcesTask::class.java,
            LinkApplicationAndroidResourcesTask::class.java,
            LinkAndroidResForBundleTask::class.java,
            GenerateResValues::class.java,
            MergeResources::class.java,
            VerifyLibraryResourcesTask::class.java,
            ProcessAndroidAppResourcesTask::class.java,
            GenerateLibraryRFileTask::class.java,
            GenerateNamespacedLibraryRFilesTask::class.java,
            LegacyShrinkBundleModuleResourcesTask::class.java,
            ParseLibraryResourcesTask::class.java,
            ExtractDeepLinksTask::class.java,
            ShrinkResourcesOldShrinkerTask::class.java,
            GenerateEmptyResourceFilesTask::class.java,
            OptimizeResourcesTask::class.java,
            ShrinkAppBundleResourcesTask::class.java,
            ShrinkResourcesNewShrinkerTask::class.java,
            PrivacySandboxSdkLinkAndroidResourcesTask::class.java,
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
