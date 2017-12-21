/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.VariantOutput
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.scope.BuildOutputs
import com.android.build.gradle.internal.scope.OutputFactory
import com.android.build.gradle.internal.scope.OutputScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.IOException

/**
 * Tests for {@see MainApkListPersistence} task.
 */
open class MainApkListPersistenceTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    var manifestFileFolder = TemporaryFolder()

    @Mock private lateinit var variantScope: VariantScope
    @Mock private lateinit var config: GradleVariantConfiguration

    private lateinit var outputScope: OutputScope
    internal lateinit var project: Project
    internal lateinit var task: MainApkListPersistence
    private lateinit var configAction: MainApkListPersistence.ConfigAction
    internal lateinit var testDir: File

    @Before
    @Throws(IOException::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        outputScope = OutputScope()
        testDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        Mockito.`when`(variantScope.getTaskName(ArgumentMatchers.any(String::class.java)))
                .thenReturn("taskFoo")
        Mockito.`when`(variantScope.outputScope).thenReturn(outputScope)
        Mockito.`when`(variantScope.splitSupportDirectory).thenReturn(temporaryFolder.root)

        task = project.tasks.create("test", MainApkListPersistence::class.java)
        configAction = MainApkListPersistence.ConfigAction(variantScope)
    }

    @Test
    fun testFullSplitPersistenceNoDisabledState() {
        val outputFactory = OutputFactory("foo", config, outputScope);
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI, "x86")))
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI,
                        "armeabi")))

        configAction.execute(task)
        assertThat(task.apkData).containsAllIn(outputScope.apkDatas)
        assertThat(task.outputFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.fullTaskAction()

        // assert persistence.
        val apkList = BuildOutputs.loadApkList(task.outputFile)
        assertThat(apkList).hasSize(2)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .filter { apkData -> apkData.isEnabled }
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86", "armeabi")
    }

    @Test
    fun testFullSplitPersistenceSomeDisabledState() {
        val outputFactory = OutputFactory("foo", config, outputScope);
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI, "x86")))
        outputFactory.addFullSplit(
                ImmutableList.of(com.android.utils.Pair.of(VariantOutput.FilterType.ABI,
                        "armeabi"))).disable()

        configAction.execute(task)
        assertThat(task.apkData).containsAllIn(outputScope.apkDatas)
        assertThat(task.outputFile.absolutePath).startsWith(temporaryFolder.root.absolutePath)

        task.fullTaskAction()

        // assert persistence.
        val apkList = BuildOutputs.loadApkList(task.outputFile)
        assertThat(apkList).hasSize(1)
        assertThat(apkList.asSequence()
                .filter { apkData -> apkData.type == VariantOutput.OutputType.FULL_SPLIT}
                .filter { apkData -> apkData.isEnabled }
                .map { apkData -> apkData.filters.asSequence().map {
                    filterData -> filterData.identifier }.first() }
                .toList())
                .containsExactly("x86")
    }
}